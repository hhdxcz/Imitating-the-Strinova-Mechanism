package com.hhdxcz.strinova.paper;

import com.hhdxcz.strinova.config.StrinovaCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理贴墙功能的方块黑名单。
 * 支持服务端黑名单维护、客户端同步、以及分享码的编解码（用于跨玩家分享黑名单配置）。
 */
public final class StrinovaWallBlacklist {
    private static final StrinovaWallBlacklist INSTANCE = new StrinovaWallBlacklist();
    private static final Set<ResourceLocation> CLIENT_BLOCKED = ConcurrentHashMap.newKeySet();

    private final Set<ResourceLocation> blocked = ConcurrentHashMap.newKeySet();

    private StrinovaWallBlacklist() {
    }

    /**
     * 获取单例实例。
     */
    public static StrinovaWallBlacklist get(MinecraftServer server) {
        return INSTANCE;
    }

    /**
     * 在客户端检查指定位置的方块是否在黑名单中。
     */
    public static boolean isBlockedClient(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isClientSide) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (id == null) {
            return false;
        }
        return CLIENT_BLOCKED.contains(id);
    }

    /**
     * 更新客户端黑名单列表。
     */
    public static void updateClient(List<ResourceLocation> list) {
        CLIENT_BLOCKED.clear();
        if (list != null) {
            CLIENT_BLOCKED.addAll(list);
        }
    }

    /**
     * 获取客户端黑名单的副本。
     */
    public static List<ResourceLocation> listClient() {
        return new ArrayList<>(CLIENT_BLOCKED);
    }

    /**
     * 在服务端检查指定位置的方块是否在黑名单中。
     */
    public static boolean isBlocked(Level level, BlockPos pos) {
        if (level == null || level.isClientSide || pos == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (id == null) {
            return false;
        }
        return INSTANCE.blocked.contains(id);
    }

    /**
     * 向黑名单添加一个方块，变更后自动保存到配置。
     */
    public boolean add(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        boolean changed = blocked.add(id);
        if (changed) {
            StrinovaCommonConfig.setWallBlacklist(list());
        }
        return changed;
    }

    /**
     * 从黑名单移除一个方块，变更后自动保存到配置。
     */
    public boolean remove(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        boolean changed = blocked.remove(id);
        if (changed) {
            StrinovaCommonConfig.setWallBlacklist(list());
        }
        return changed;
    }

    /**
     * 清空黑名单，返回被清除的条目数量。
     */
    public int clear() {
        int size = blocked.size();
        if (size > 0) {
            blocked.clear();
            StrinovaCommonConfig.setWallBlacklist(list());
        }
        return size;
    }

    /**
     * 用新列表替换整个黑名单，返回新列表的大小。
     */
    public int replace(List<ResourceLocation> list) {
        blocked.clear();
        if (list != null) {
            blocked.addAll(list);
        }
        StrinovaCommonConfig.setWallBlacklist(this.list());
        return blocked.size();
    }

    /**
     * 获取黑名单的副本。
     */
    public List<ResourceLocation> list() {
        return new ArrayList<>(blocked);
    }

    /**
     * 直接替换服务端黑名单（不触发配置保存）。
     */
    public static void replaceServerList(List<ResourceLocation> list) {
        INSTANCE.blocked.clear();
        if (list != null) {
            INSTANCE.blocked.addAll(list);
        }
    }

    /**
     * 将黑名单方块列表编码为分享码。
     * 采用 delta 编码和组合数编码两种方式，选择更短的输出。
     */
    public static String encodeShareCode(List<ResourceLocation> list) {
        List<Integer> ids = new ArrayList<>();
        if (list != null) {
            for (ResourceLocation id : list) {
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (block != null && block != Blocks.AIR) {
                    ids.add(BuiltInRegistries.BLOCK.getId(block));
                }
            }
        }
        ids.sort(Integer::compareTo);
        if (ids.isEmpty()) {
            return "";
        }

        BitWriter delta = new BitWriter();
        writeDelta(delta, ids);
        BitWriter comb = new BitWriter();
        writeCombinadic(comb, ids);

        BitWriter out = new BitWriter();
        if (comb.bitPos < delta.bitPos) {
            out.writeBit(1);
            out.copyFrom(comb);
        } else {
            out.writeBit(0);
            out.copyFrom(delta);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /**
     * 从分享码解码为黑名单方块列表。
     */
    public static List<ResourceLocation> decodeShareCode(String code) {
        if (code == null || code.isBlank()) {
            return new ArrayList<>();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(code);
            BitReader reader = new BitReader(bytes);
            int flag = reader.readBit();
            List<Integer> ids = flag == 1 ? readCombinadic(reader) : readDelta(reader);
            List<ResourceLocation> list = new ArrayList<>(ids.size());
            for (int id : ids) {
                Block block = BuiltInRegistries.BLOCK.byId(id);
                if (block == null || block == Blocks.AIR) {
                    continue;
                }
                ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
                if (rl != null) {
                    list.add(rl);
                }
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 使用 delta 编码将方块 ID 列表写入 BitWriter。
     */
    private static void writeDelta(BitWriter w, List<Integer> ids) {
        int k = ids.size();
        int[] values = new int[k];
        int maxValue = 0;
        int prev = -1;
        int i = 0;
        for (int id : ids) {
            int value = id - prev - 1;
            values[i++] = value;
            if (value > maxValue) {
                maxValue = value;
            }
            prev = id;
        }
        int bits = bitsNeeded(maxValue);
        w.writeVarint(k);
        w.write(bits, 5);
        for (int value : values) {
            w.write(value, bits);
        }
    }

    /**
     * 从 BitReader 读取 delta 编码的方块 ID 列表。
     */
    private static List<Integer> readDelta(BitReader r) {
        int k = r.readVarint();
        int bits = r.read(5);
        List<Integer> ids = new ArrayList<>(k);
        int prev = -1;
        for (int i = 0; i < k; i++) {
            int value = r.read(bits);
            int id = prev + value + 1;
            prev = id;
            ids.add(id);
        }
        return ids;
    }

    /**
     * 使用组合数编码将方块 ID 列表写入 BitWriter。
     */
    private static void writeCombinadic(BitWriter w, List<Integer> ids) {
        int k = ids.size();
        BigInteger index = BigInteger.ZERO;
        for (int i = 0; i < k; i++) {
            index = index.add(comb(ids.get(i), i + 1));
        }
        int bl = index.bitLength();
        w.writeVarint(k);
        w.writeVarint(bl);
        for (int i = bl - 1; i >= 0; i--) {
            w.writeBit(index.testBit(i) ? 1 : 0);
        }
    }

    /**
     * 从 BitReader 读取组合数编码的方块 ID 列表。
     */
    private static List<Integer> readCombinadic(BitReader r) {
        int k = r.readVarint();
        int bl = r.readVarint();
        BigInteger index = BigInteger.ZERO;
        for (int i = 0; i < bl; i++) {
            if (r.readBit() != 0) {
                index = index.setBit(bl - 1 - i);
            }
        }
        int[] ids = new int[k];
        for (int i = k; i >= 1; i--) {
            int c = largestC(i, index);
            index = index.subtract(comb(c, i));
            ids[i - 1] = c;
        }
        List<Integer> list = new ArrayList<>(k);
        for (int id : ids) {
            list.add(id);
        }
        return list;
    }

    /**
     * 计算组合数 C(n, r)。
     */
    private static BigInteger comb(int n, int r) {
        if (r < 0 || n < r) {
            return BigInteger.ZERO;
        }
        if (r == 0 || r == n) {
            return BigInteger.ONE;
        }
        if (r > n - r) {
            r = n - r;
        }
        BigInteger result = BigInteger.ONE;
        for (int i = 1; i <= r; i++) {
            result = result.multiply(BigInteger.valueOf(n - r + i)).divide(BigInteger.valueOf(i));
        }
        return result;
    }

    /**
     * 在组合数编码中反推最大的 n，使得 C(n, r) <= index。
     */
    private static int largestC(int r, BigInteger index) {
        int lo = r - 1;
        int hi = r;
        while (hi < MAX_N && comb(hi, r).compareTo(index) <= 0) {
            lo = hi;
            hi = Math.min(MAX_N, hi * 2 + 1);
        }
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (comb(mid, r).compareTo(index) <= 0) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /**
     * 计算表示指定值所需的最少位数。
     */
    private static int bitsNeeded(int maxValue) {
        return maxValue == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(maxValue);
    }

    /** 组合数编码中 n 的最大值。 */
    private static final int MAX_N = 1 << 20;

    /**
     * 位写入器，支持按位写入、变长整数编码。
     */
    private static final class BitWriter {
        private byte[] buf = new byte[16];
        private int bitPos;

        void write(int value, int bits) {
            for (int i = bits - 1; i >= 0; i--) {
                writeBit((value >>> i) & 1);
            }
        }

        void writeVarint(int value) {
            while (true) {
                int b = value & 0x7F;
                value >>>= 7;
                if (value != 0) {
                    write(b | 0x80, 8);
                } else {
                    write(b, 8);
                    break;
                }
            }
        }

        void writeBit(int bit) {
            int byteIndex = bitPos >>> 3;
            if (byteIndex >= buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            if (bit != 0) {
                buf[byteIndex] |= (byte) (1 << (bitPos & 7));
            }
            bitPos++;
        }

        int getBit(int index) {
            int byteIndex = index >>> 3;
            if (byteIndex >= buf.length) {
                return 0;
            }
            return (buf[byteIndex] >>> (index & 7)) & 1;
        }

        void copyFrom(BitWriter other) {
            for (int i = 0; i < other.bitPos; i++) {
                writeBit(other.getBit(i));
            }
        }

        byte[] toByteArray() {
            return Arrays.copyOf(buf, (bitPos + 7) >>> 3);
        }
    }

    /**
     * 位读取器，支持按位读取、变长整数解码。
     */
    private static final class BitReader {
        private final byte[] buf;
        private int bitPos;

        BitReader(byte[] buf) {
            this.buf = buf;
        }

        int read(int bits) {
            int value = 0;
            for (int i = 0; i < bits; i++) {
                value = (value << 1) | readBit();
            }
            return value;
        }

        int readVarint() {
            int result = 0;
            int shift = 0;
            while (true) {
                int b = read(8);
                result |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }
            return result;
        }

        int readBit() {
            int byteIndex = bitPos >>> 3;
            int bitIndex = bitPos & 7;
            bitPos++;
            if (byteIndex >= buf.length) {
                return 0;
            }
            return (buf[byteIndex] >>> bitIndex) & 1;
        }
    }
}