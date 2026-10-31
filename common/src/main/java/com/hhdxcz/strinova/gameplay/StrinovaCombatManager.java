package com.hhdxcz.strinova.gameplay;

import com.hhdxcz.strinova.client.hud.StrinovaTaczCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// 战斗状态管理器（服务端权威）：按玩家 UUID 保存护甲、KDA、绝招点与受击归属
// 只维护内存数据，不直接操作玩家实体；由 PlayerMixin / 平台事件钩子调用。
// 并发约定：STATE 使用 ConcurrentHashMap；单个玩家的字段更新以 Holder 实例为锁，
// 因此即使将来从非主线程调用也不会互相踩踏，但推荐统一在服务端主线程调用。
public final class StrinovaCombatManager {

    // ===== 数值常量（与任务约定一致） =====
    /** 默认护甲上限（点）。 */
    private static final float DEFAULT_MAX_ARMOR = 80.0F;
    /** 默认绝招点上限。 */
    private static final int DEFAULT_ULTIMATE_MAX = 7;
    /** 每 40 点实际伤害换算 1 点绝招点。 */
    private static final float ULTIMATE_DAMAGE_PER_POINT = 40.0F;
    /** 护甲吸收比例：50% 伤害由护甲承担。 */
    private static final float ARMOR_ABSORB_RATIO = 0.5F;
    /** 1 点 MC 伤害 = 7.5 护甲点。 */
    private static final float ARMOR_POINTS_PER_DAMAGE = 7.5F;
    /** 距上次受击超过 3 秒才开始回甲。 */
    private static final long ARMOR_REGEN_DELAY_MS = 3000L;
    /** 回甲速度：每秒 1 点。 */
    private static final float ARMOR_REGEN_PER_SECOND = 1.0F;
    /** 助攻判定窗口：死亡前 10 秒内造成过实际伤害。 */
    private static final long ASSIST_WINDOW_MS = 10_000L;
    /** kill feed 待消费队列上限，防止无人消费时无限增长。 */
    private static final int MAX_PENDING_KILL_EVENTS = 256;
    /** 每个攻击者待消费伤害事件上限（每条命中都会产生，网络层按 tick 取走，留足缓冲即可）。 */
    private static final int MAX_PENDING_DAMAGE_EVENTS = 64;
    /** 伤害类型 id 中判定爆头的匹配片段（统一小写匹配，如 tacz:headshot / xxx:head_shot_damage）。 */
    private static final String[] HEADSHOT_KEYS = {"headshot", "head_shot"};
    /** 阵亡信息中无法确定击杀者时的占位名。 */
    private static final String ENVIRONMENT_NAME = "环境";
    /** 击杀者主手无物品时的武器名。 */
    private static final String MELEE_WEAPON_NAME = "近战";

    /** 全部在线玩家的战斗状态。 */
    private static final Map<UUID, Holder> STATES = new ConcurrentHashMap<>();
    /** 待网络层/HUD 消费的击杀事件队列（每 tick drain 一次）。 */
    private static final ConcurrentLinkedQueue<KillEvent> KILL_EVENTS = new ConcurrentLinkedQueue<>();
    /**
     * 各攻击者待消费的命中事件队列（供网络层向该玩家推送真实伤害数字）。
     * 每个攻击者最多保留 {@link #MAX_PENDING_DAMAGE_EVENTS} 条，旧事件在无人消费时被丢弃，避免无限增长。
     */
    private static final Map<UUID, List<DamageEvent>> DAMAGE_EVENTS = new ConcurrentHashMap<>();
    /** 回甲节流：上次结算所在的 server 秒（server tick / 20）。 */
    private static volatile int lastRegenSecond = Integer.MIN_VALUE;

    private StrinovaCombatManager() {
    }

    // ==================== 对外数据结构（API 约定，字段顺序不可改） ====================

    // 玩家战斗状态快照：护甲 / 上限 / KDA / 绝招点
    public record PlayerState(UUID id, float armor, float maxArmor, int kills, int deaths, int assists,
                              int ultimatePoints, int ultimateMax) {
    }

    /**
     * 单次命中事件（用于伤害数字与对局数据统计）。
     *
     * @param attackerId 攻击者 UUID（可能为 null：环境伤害）
     * @param amount     实际造成伤害（护甲吸收后）
     * @param headshot   是否命中头部
     * @param mitigated  是否被护甲吸收过
     */
    public record DamageEvent(UUID attackerId, float amount, boolean headshot, boolean mitigated, long timeMs) {
    }

    // 击杀事件：killer/weapon/victim 均为展示名，headshot 为命中部位标记
    public record KillEvent(String killer, String weapon, String victim, boolean headshot) {
    }

    // ==================== 生命周期 ====================

    // 登录初始化：护甲回满、清受击时间、清除助攻候选，并保证 map 中有记录（保留已存在的 KDA/绝招点）
    public static void ensure(UUID playerId) {
        if (playerId == null) {
            return;
        }
        STATES.compute(playerId, (id, holder) -> {
            Holder h = holder != null ? holder : new Holder(id);
            synchronized (h) {
                h.armor = h.maxArmor;
                h.lastHitMillis = 0L;
                h.recentDamagers.clear();
            }
            return h;
        });
    }

    // 玩家退出：移除其状态，并清理其他玩家受击记录里对该玩家的引用
    public static void remove(UUID playerId) {
        if (playerId == null) {
            return;
        }
        STATES.remove(playerId);
        // 该玩家的待消费伤害事件随退出一起丢弃，避免网络层继续向已离线玩家推送
        DAMAGE_EVENTS.remove(playerId);
        for (Holder holder : STATES.values()) {
            holder.recentDamagers.remove(playerId);
        }
    }

    // 清空全部战斗状态与待消费击杀事件（服务器停止/切换存档时调用）
    public static void clearAll() {
        STATES.clear();
        KILL_EVENTS.clear();
        DAMAGE_EVENTS.clear();
        lastRegenSecond = Integer.MIN_VALUE;
    }

    // 每 server tick 调用：内部按 server 秒节流，每秒最多结算一次回甲
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int serverSecond = server.getTickCount() / 20;
        if (serverSecond == lastRegenSecond) {
            return; // 同一秒内只结算一次，避免每 tick 全量遍历
        }
        lastRegenSecond = serverSecond;

        long now = System.currentTimeMillis();
        for (Holder holder : STATES.values()) {
            synchronized (holder) {
                // 距上次受击超过 3 秒后每秒 +1 点，直到护甲上限
                if (holder.armor < holder.maxArmor && now - holder.lastHitMillis > ARMOR_REGEN_DELAY_MS) {
                    holder.armor = Math.min(holder.maxArmor, holder.armor + ARMOR_REGEN_PER_SECOND);
                }
                // 顺带清理过期的助攻候选，避免长时间不死亡时记录堆积
                holder.recentDamagers.entrySet().removeIf(entry -> now - entry.getValue() > ASSIST_WINDOW_MS);
            }
        }
    }

    // ==================== 查询接口（全部 null 安全） ====================

    // 查询玩家状态；不存在时返回默认快照（护甲 0/80、KDA 0、绝招点 0/7）
    public static PlayerState state(UUID playerId) {
        Holder holder = playerId == null ? null : STATES.get(playerId);
        if (holder == null) {
            return new PlayerState(playerId, 0.0F, DEFAULT_MAX_ARMOR, 0, 0, 0, 0, DEFAULT_ULTIMATE_MAX);
        }
        synchronized (holder) {
            return holder.snapshot();
        }
    }

    public static float armor(UUID playerId) {
        return state(playerId).armor();
    }

    public static float maxArmor(UUID playerId) {
        return state(playerId).maxArmor();
    }

    public static int kills(UUID playerId) {
        return state(playerId).kills();
    }

    public static int deaths(UUID playerId) {
        return state(playerId).deaths();
    }

    public static int assists(UUID playerId) {
        return state(playerId).assists();
    }

    public static int ultimatePoints(UUID playerId) {
        return state(playerId).ultimatePoints();
    }

    public static int ultimateMax(UUID playerId) {
        return state(playerId).ultimateMax();
    }

    // ==================== 战斗结算 ====================

    // 受击结算：护甲承担 50% 伤害（按 1 MC 伤害 = 7.5 护甲点换算），返回扣甲后应实际造成的 MC 伤害；
    // 同时记录最近攻击者（用于助攻/击杀归属）、给攻击者累积绝招点（按实际伤害，每 40 点 +1，上限 7）、
    // 累计输出分/技能分，并向攻击者队列 push 一条 DamageEvent（真实伤害数字来源）。
    // 兼容旧调用点：3 参重载委托给 4 参重载（source=null，无法判定爆头/伤害类型，按非枪械计）。
    public static float handleIncomingDamage(ServerPlayer victim, Entity attacker, float mcDamage) {
        return handleIncomingDamage(victim, attacker, null, mcDamage);
    }

    /**
     * 带 DamageSource 的受击结算（推荐入口）：护甲结算后累计输出分/技能分并产生 DamageEvent。
     *
     * @param attacker 攻击者实体（通常传 source.getEntity()）；可为 null，内部会回退到 source.getEntity()
     * @param source   伤害来源；为 null 表示调用方拿不到来源（爆头判定为 false、枪械判定为 false）
     */
    public static float handleIncomingDamage(ServerPlayer victim, Entity attacker, DamageSource source, float mcDamage) {
        if (victim == null) {
            return mcDamage;
        }
        // 非法/非正伤害直接透传，避免 NaN、负数污染护甲与统计
        if (!Float.isFinite(mcDamage) || mcDamage <= 0.0F) {
            return mcDamage;
        }

        UUID victimId = victim.getUUID();
        if (victimId == null) {
            return mcDamage;
        }
        long now = System.currentTimeMillis();
        // 命中部位：TACZ 等模组可能把爆头信息挂在 DamageSource/直接实体/NBT 上；反射探测失败一律 false
        boolean headshot = isHeadshotDamageSource(source);
        float actualDamage;
        boolean mitigated;

        // 受害方护甲结算（未 ensure 时惰性建立 80 点护甲，避免登录钩子缺失导致护甲系统静默失效）
        Holder victimHolder = holderOrCreate(victimId);
        synchronized (victimHolder) {
            float armorPoints = Math.min(victimHolder.armor,
                    mcDamage * ARMOR_ABSORB_RATIO * ARMOR_POINTS_PER_DAMAGE);
            float absorbedDamage = armorPoints / ARMOR_POINTS_PER_DAMAGE;
            victimHolder.armor = Math.max(0.0F, victimHolder.armor - armorPoints);
            victimHolder.lastHitMillis = now; // 记录受击时间：即使护甲为 0 也要刷新，回甲需从最后一次受击起算
            actualDamage = Math.max(0.0F, mcDamage - absorbedDamage);
            mitigated = absorbedDamage > 0.0F;
        }

        // 攻击方统计（支持直接攻击者与弹射物的 owner；attacker 缺失时回退到 DamageSource.getEntity()）
        Entity attackerEntity = attacker != null ? attacker : (source == null ? null : source.getEntity());
        ServerPlayer attackerPlayer = resolvePlayer(attackerEntity);
        if (attackerPlayer != null) {
            UUID attackerId = attackerPlayer.getUUID();
            if (attackerId != null && !attackerId.equals(victimId)) {
                boolean gunDamage = isGunDamage(source, attackerEntity);
                Holder attackerHolder = holderOrCreate(attackerId);
                synchronized (attackerHolder) {
                    if (attackerHolder.ultimatePoints < attackerHolder.ultimateMax) {
                        attackerHolder.ultimateDamage += actualDamage;
                        while (attackerHolder.ultimatePoints < attackerHolder.ultimateMax
                                && attackerHolder.ultimateDamage >= ULTIMATE_DAMAGE_PER_POINT) {
                            attackerHolder.ultimateDamage -= ULTIMATE_DAMAGE_PER_POINT;
                            attackerHolder.ultimatePoints++;
                        }
                    }
                    if (attackerHolder.ultimatePoints >= attackerHolder.ultimateMax) {
                        attackerHolder.ultimateDamage = 0.0F; // 满点后不再囤积溢出伤害
                    }
                    // 输出分：对玩家造成的真实伤害；技能分：非枪械伤害（技能/绝招/爆炸等）
                    attackerHolder.damageDealt += actualDamage;
                    if (!gunDamage) {
                        attackerHolder.skillDamage += actualDamage;
                    }
                }
                if (actualDamage > 0.0F) {
                    victimHolder.recentDamagers.put(attackerId, now);
                    pushDamageEvent(new DamageEvent(attackerId, actualDamage, headshot, mitigated, now));
                }
            }
        }
        return actualDamage;
    }

    // 记录死亡：victim deaths+1、killer kills+1，产生 KillEvent 并入队；
    // 同时结算助攻（死亡前 10 秒内造成过实际伤害的第三方玩家 +1）。
    // 兼容旧调用点：2 参重载无法拿到命中部位信息，headshot 记为 false。
    public static void recordDeath(ServerPlayer victim, Entity killer) {
        recordDeath(victim, killer, false);
    }

    /**
     * 带命中部位的死亡记录（推荐入口）：PlayerMixin / 平台死亡钩子用 DamageSource 探测爆头后传入。
     *
     * @param headshot 已知的爆头标记；调用方探测不到时应传 false（绝不让探测失败影响 KDA 结算）
     */
    public static void recordDeath(ServerPlayer victim, Entity killer, boolean headshot) {
        if (victim == null) {
            return;
        }
        UUID victimId = victim.getUUID();
        if (victimId == null) {
            return;
        }
        long now = System.currentTimeMillis();

        Holder victimHolder = holderOrCreate(victimId);
        synchronized (victimHolder) {
            victimHolder.deaths++;
        }

        // 解析击杀者（直接玩家或弹射物 owner），自己杀自己不计 kills
        ServerPlayer killerPlayer = resolvePlayer(killer);
        UUID killerId = killerPlayer != null ? killerPlayer.getUUID() : null;
        if (killerId != null && !killerId.equals(victimId)) {
            Holder killerHolder = holderOrCreate(killerId);
            synchronized (killerHolder) {
                killerHolder.kills++;
            }
        }

        // 助攻结算：窗口内对死者造成过实际伤害、且既不是死者也不是击杀者的玩家
        for (Map.Entry<UUID, Long> entry : victimHolder.recentDamagers.entrySet()) {
            UUID candidateId = entry.getKey();
            Long hitAt = entry.getValue();
            if (candidateId == null || hitAt == null) {
                continue;
            }
            if (candidateId.equals(victimId) || candidateId.equals(killerId)) {
                continue;
            }
            if (now - hitAt > ASSIST_WINDOW_MS) {
                continue;
            }
            Holder candidateHolder = STATES.get(candidateId);
            if (candidateHolder == null) {
                continue;
            }
            synchronized (candidateHolder) {
                candidateHolder.assists++;
            }
        }
        victimHolder.recentDamagers.clear();

        // KillEvent：killer/weapon/victim 使用展示名；主手物品为空时武器名回退为"近战"
        String victimName = nameOf(victim, "未知玩家");
        String killerName = killerPlayer != null ? nameOf(killerPlayer, ENVIRONMENT_NAME) : nameOf(killer, ENVIRONMENT_NAME);
        Entity weaponSource = killerPlayer != null ? killerPlayer : killer;
        KillEvent event = new KillEvent(killerName, weaponNameOf(weaponSource), victimName, headshot);

        KILL_EVENTS.add(event);
        while (KILL_EVENTS.size() > MAX_PENDING_KILL_EVENTS) {
            KILL_EVENTS.poll(); // 丢弃最旧事件，防止无人消费时无限增长
        }
    }

    // 重生：护甲回满、清受击/助攻历史，保留 KDA 与绝招点
    public static void respawn(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ensure(player.getUUID());
    }

    // ==================== 对局数据（输出分 / 技能分 / 伤害事件） ====================

    /** 累计输出分（对玩家造成的真实伤害，四舍五入取整；无记录返回 0）。 */
    public static int damageDealt(UUID playerId) {
        Holder holder = playerId == null ? null : STATES.get(playerId);
        if (holder == null) {
            return 0;
        }
        synchronized (holder) {
            return Math.round(holder.damageDealt);
        }
    }

    /** 累计技能分（非枪械伤害，如技能/绝招/爆炸；四舍五入取整，无记录返回 0）。 */
    public static int skillDamage(UUID playerId) {
        Holder holder = playerId == null ? null : STATES.get(playerId);
        if (holder == null) {
            return 0;
        }
        synchronized (holder) {
            return Math.round(holder.skillDamage);
        }
    }

    /** 取出并清空某攻击者待消费的命中事件（用于向该玩家推送伤害数字）；无事件返回空列表。 */
    public static List<DamageEvent> drainDamageEvents(UUID attackerId) {
        if (attackerId == null) {
            return List.of();
        }
        List<DamageEvent> queue = DAMAGE_EVENTS.get(attackerId);
        if (queue == null) {
            return List.of();
        }
        synchronized (queue) {
            if (queue.isEmpty()) {
                return List.of();
            }
            List<DamageEvent> drained = new ArrayList<>(queue);
            queue.clear();
            return drained;
        }
    }

    // 取出并清空待消费的击杀事件；无事件时返回空列表（永不为 null）
    public static List<KillEvent> drainKillEvents() {
        List<KillEvent> events = new ArrayList<>();
        KillEvent event;
        while ((event = KILL_EVENTS.poll()) != null) {
            events.add(event);
        }
        return events;
    }

    // ==================== 命中部位 / 伤害类型判定（全部反射容错，绝不抛异常） ====================

    /**
     * 反射探测一次伤害是否命中头部。TACZ 等模组没有编译期依赖，只能从 DamageSource / 直接实体 / NBT 上找常见标记：
     * <ul>
     *   <li>伤害类型 id（如 tacz:headshot）含 headshot；</li>
     *   <li>DamageSource 子类上的 isHeadshot()/getHeadshot()/isHeadShot() 方法或 headshot/headShot 字段；</li>
     *   <li>直接实体（子弹）上的同类方法/字段、实体持久化 NBT（headshot/Headshot/isHeadshot 键）、
     *       实体存档 NBT、实体 scoreboard tag；</li>
     * </ul>
     * 找不到返回 false；任何反射异常都被吞掉，绝不影响伤害结算。
     */
    public static boolean isHeadshotDamageSource(DamageSource source) {
        if (source == null) {
            return false;
        }
        try {
            // 1) 自定义伤害类型 id：tacz:headshot / xxx:headshot_damage 等
            if (containsHeadshot(damageTypeId(source))) {
                return true;
            }
            // 2) DamageSource 自身（少数模组用自定义子类携带命中部位）
            if (probeHeadshotObject(source)) {
                return true;
            }
            // 3) 直接实体（TACZ 子弹等弹射物）：爆头标记通常挂在子弹实体/NBT 上；
            //    不做攻击者实体探测（玩家实体没有该标记，且序列化玩家 NBT 开销大）
            Entity direct = source.getDirectEntity();
            if (probeHeadshotEntity(direct)) {
                return true;
            }
        } catch (Throwable ignored) {
            // 探测失败一律按未爆头处理
        }
        return false;
    }

    // 反射探测任意对象（DamageSource 等）上的爆头方法/字段
    private static boolean probeHeadshotObject(Object target) {
        try {
            for (String name : new String[]{"isHeadshot", "getHeadshot", "isHeadShot", "getHeadShot",
                    "isHeadshotHit", "isHeadShotHit"}) {
                try {
                    var method = target.getClass().getMethod(name);
                    method.setAccessible(true);
                    Object value = method.invoke(target);
                    if (value instanceof Boolean flag && flag) {
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                }
            }
            for (Class<?> clazz = target.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (String name : new String[]{"headshot", "headShot", "isHeadshot", "isHeadShot", "head_shot"}) {
                    try {
                        var field = clazz.getDeclaredField(name);
                        field.setAccessible(true);
                        Object value = field.get(target);
                        if (value instanceof Boolean flag && flag) {
                            return true;
                        }
                    } catch (NoSuchFieldException ignored) {
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // 实体爆头探测：复用 StrinovaTaczCompat 的 TACZ 方法/字段/NBT 探测（客户端兼容层不可用时安全忽略）
    private static boolean probeHeadshotEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return StrinovaTaczCompat.probeHeadshot(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 判断一次伤害是否属于「枪械伤害」：子弹类实体或 tacz/bullet/gun 命名空间的伤害类型视为枪械；
     * 直伤只有 player_attack（近战/武器直击）算枪械，其余（技能/爆炸/摔落/箭矢等）算技能伤害。
     */
    private static boolean isGunDamage(DamageSource source, Entity attacker) {
        if (source == null) {
            return false; // 来源未知：按非枪械（技能）计
        }
        Entity direct = source.getDirectEntity();
        // 子弹类实体（TACZ EntityKineticBullet / 其他枪械模组的 Bullet）优先判定为枪械
        if (isBulletLike(direct) || isBulletLike(attacker)) {
            return true;
        }
        if (containsTaczOrBullet(damageTypeId(source))) {
            return true;
        }
        // 直伤：直接实体就是攻击者且伤害类型为 player_attack 时视作枪械（近战/武器直击），否则按技能
        return direct != null && direct == attacker && isPlayerAttack(source);
    }

    // 子弹类实体识别：优先问 TACZ 兼容层，再用类名兜底（类名含 bullet 即视为子弹）
    private static boolean isBulletLike(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            if (StrinovaTaczCompat.isTaczBullet(entity)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Class<?> clazz = entity.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                if (clazz.getName().toLowerCase(Locale.ROOT).contains("bullet")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // 伤害类型 id 是否属于枪械（tacz 命名空间或 bullet/gun 关键字）
    private static boolean containsTaczOrBullet(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return false;
        }
        String lower = typeId.toLowerCase(Locale.ROOT);
        return lower.startsWith("tacz:") || lower.contains("bullet") || lower.contains("gun");
    }

    private static boolean isPlayerAttack(DamageSource source) {
        try {
            return source.is(DamageTypes.PLAYER_ATTACK);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // 取伤害类型注册 id（如 "tacz:bullet"），拿不到时返回 ""（不抛异常）
    private static String damageTypeId(DamageSource source) {
        if (source == null) {
            return "";
        }
        try {
            String msgId = source.getMsgId();
            return msgId == null ? "" : msgId;
        } catch (Throwable ignored) {
            return "";
        }
    }

    // 文本是否含爆头标记（大小写/下划线/连字符不敏感）
    private static boolean containsHeadshot(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String key : HEADSHOT_KEYS) {
            if (lower.contains(key)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 内部工具 ====================

    // 取得玩家 Holder；不存在时惰性创建（护甲 80、绝招点 0），保证结算不会因登录钩子缺失而丢状态
    private static Holder holderOrCreate(UUID playerId) {
        Holder holder = STATES.get(playerId);
        if (holder != null) {
            return holder;
        }
        Holder created = new Holder(playerId);
        Holder existing = STATES.putIfAbsent(playerId, created);
        return existing != null ? existing : created;
    }

    // 向攻击者队列 push 一条命中事件（每个攻击者最多 MAX_PENDING_DAMAGE_EVENTS 条，超出丢弃最旧）
    private static void pushDamageEvent(DamageEvent event) {
        if (event == null || event.attackerId() == null) {
            return;
        }
        List<DamageEvent> queue = DAMAGE_EVENTS.computeIfAbsent(event.attackerId(), id -> new ArrayList<>());
        synchronized (queue) {
            while (queue.size() >= MAX_PENDING_DAMAGE_EVENTS) {
                queue.remove(0);
            }
            queue.add(event);
        }
    }

    // 解析攻击者为服务端玩家：直接玩家优先生效，否则尝试取弹射物（箭/子弹等）的 owner
    private static ServerPlayer resolvePlayer(Entity attacker) {
        if (attacker == null) {
            return null;
        }
        if (attacker instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        if (attacker instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        // 兼容第三方枪械（如 TACZ）的自定义子弹实体：owner/shooter/thrower 字段或 getter
        try {
            for (String methodName : new String[]{"getOwner", "getShooter", "getThrower"}) {
                try {
                    var method = attacker.getClass().getMethod(methodName);
                    Object value = method.invoke(attacker);
                    if (value instanceof ServerPlayer owner) {
                        return owner;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (String fieldName : new String[]{"owner", "shooter", "thrower"}) {
                try {
                    var field = attacker.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(attacker);
                    if (value instanceof ServerPlayer owner) {
                        return owner;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // 取实体展示名，空白/缺失时返回 fallback
    private static String nameOf(Entity entity, String fallback) {
        if (entity == null) {
            return fallback;
        }
        var name = entity.getName();
        if (name == null) {
            return fallback;
        }
        String text = name.getString();
        return text == null || text.isBlank() ? fallback : text;
    }

    // 取击杀者主手物品显示名；无物品/名字为空时回退"近战"，击杀者为 null 时回退"环境"
    private static String weaponNameOf(Entity source) {
        if (source == null) {
            return ENVIRONMENT_NAME;
        }
        if (source instanceof LivingEntity living) {
            ItemStack stack = living.getMainHandItem();
            if (stack != null && !stack.isEmpty()) {
                var hoverName = stack.getHoverName();
                String text = hoverName == null ? null : hoverName.getString();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return MELEE_WEAPON_NAME;
    }

    // 玩家战斗状态内部存储（不可变 API 快照由 snapshot() 生成；字段更新需 synchronized(holder)）
    private static final class Holder {
        private final UUID playerId;
        private final float maxArmor;
        private final int ultimateMax;
        private float armor;
        private int kills;
        private int deaths;
        private int assists;
        private int ultimatePoints;
        private float ultimateDamage;
        private float damageDealt;
        private float skillDamage;
        private long lastHitMillis;
        private final Map<UUID, Long> recentDamagers = new ConcurrentHashMap<>();

        private Holder(UUID playerId) {
            this.playerId = playerId;
            this.maxArmor = DEFAULT_MAX_ARMOR;
            this.ultimateMax = DEFAULT_ULTIMATE_MAX;
            this.armor = DEFAULT_MAX_ARMOR;
        }

        private PlayerState snapshot() {
            return new PlayerState(playerId, armor, maxArmor, kills, deaths, assists, ultimatePoints, ultimateMax);
        }
    }
}
