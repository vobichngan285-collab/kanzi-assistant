package com.vobichngan.bluestar.m02slab;

import java.util.Arrays;
import java.util.Locale;

/**
 * Minimal interoperability implementation independently derived from the
 * user-supplied official HeyCyan APK, not copied from its source tree.
 *
 * Verified (static bytecode trace, official APK v1.0.24-c):
 *   SendCommandViaBle.takePhoto(v)     -> configSendCommand(v, 34/0x22)
 *   SendCommandViaBle.getWifiInfo(v)   -> configSendCommand(v, 57/0x39)
 *   Command.Param.TAKE_PHOTO_THUMBNAIL -> 48 / 0x30
 *   Command.Param.GET_WIFI_P2P         -> 49 / 0x31
 *   Command.Param.GET_WIFI_AP          -> 48 / 0x30
 *   Datagram.convertLeHeaderToBytes() -> AB 55, big-endian length
 *                                        (command + payload + checksum),
 *                                        command, payload, sum mod 256.
 *   BleCore.setupCharacteristics()     -> AA13 CMD_WRITE, AA14 CMD_NOTIFY,
 *                                        AA15 PHOTO_NOTIFY.
 * Runtime command responses on the specific user's M02S remain untested.
 */
public final class M02sBleWire {
    private M02sBleWire() {}

    public static final int CMD_TAKE_PHOTO = 0x22;
    public static final int CMD_GET_WIFI_INFO = 0x39;

    public static byte[] photoThumbnail() {
        return frame(CMD_TAKE_PHOTO, 0x30);
    }

    /** Eyevue E09 open-source source: TAKE_PHOTO high-quality -> AA15 JPEG transfer. */
    public static byte[] e09BlePhoto() { return frame(0x22, 0x31); }

    /** Eyevue E09 open-source source: APP_LIVE AP mode -> 0x25 SSID -> RTSP /h264. */
    public static byte[] e09AppLiveAp() { return frame(0x67, 0x30); }

    /** E09/HeyCyan documented NON-DELETING Wi-Fi session finish (0x44/0x30/0x01).
     * Never expose 0x44/0x30/0x00: that variant can delete media. */
    public static byte[] e09SafeTransferFinish() {
        return new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,1,0x75};
    }

    public static byte[] wifiP2pInfo() {
        return frame(CMD_GET_WIFI_INFO, 0x31);
    }

    public static byte[] wifiApInfo() {
        return frame(CMD_GET_WIFI_INFO, 0x30);
    }

    private static byte[] frame(int command, int parameter) {
        int check = (command + parameter) & 0xff;
        return new byte[]{
                (byte) 0xAB, 0x55, 0, 3,
                (byte) command, (byte) parameter, (byte) check
        };
    }

    public static boolean isExactAuthorizedCommand(byte[] bytes) {
        return Arrays.equals(bytes, photoThumbnail())
                || Arrays.equals(bytes, wifiP2pInfo())
                || Arrays.equals(bytes, wifiApInfo())
                || Arrays.equals(bytes, e09BlePhoto())
                || Arrays.equals(bytes, e09AppLiveAp())
                || Arrays.equals(bytes, e09SafeTransferFinish());
    }

    public static final class ParsedFrame {
        public final int command;
        public final int payloadLength;
        public final boolean checksumOk;
        private final byte[] payload;

        private ParsedFrame(int command, byte[] payload, boolean checksumOk) {
            this.command = command;
            this.payload = payload;
            this.payloadLength = payload.length;
            this.checksumOk = checksumOk;
        }

        /** OEM 0x25: decoded as UTF-8 by WifiCmdBean.recoverByBytes().
         * Only return in process memory. Never print or persist it. */
        public String wifiSsidInMemory() {
            if (command != 0x25 || !checksumOk) return null;
            return M02sWifiPolicy.decodeSsid(payload);
        }

        /** Only the non-sensitive photo-related status fields are printable. */
        public String safeSummary() {
            String name;
            switch (command) {
                case 0x22: name = "拍照指令响应"; break;
                case 0x39: name = "Wi-Fi信息请求应答（SSID/凭证已隐藏）"; break;
                case 0x25: name = "眼镜SSID信息（已隐藏）"; break;
                case 0x42: name = "照片计数/序号候选"; break;
                case 0x45: name = "操作状态候选"; break;
                default: name = "未知事件（内容已隐藏）"; break;
            }
            StringBuilder s = new StringBuilder();
            s.append(String.format(Locale.ROOT, "命令=0x%02X %s；载荷长度=%d；校验=%s",
                    command, name, payloadLength, checksumOk ? "通过" : "失败"));
            if (checksumOk && (command == 0x22 || command == 0x42 || command == 0x45)
                    && payload.length <= 4) {
                s.append("；安全预览=");
                for (int i = 0; i < payload.length; i++) {
                    if (i != 0) s.append(' ');
                    s.append(String.format(Locale.ROOT, "%02X", payload[i] & 0xff));
                }
            }
            return s.toString();
        }
    }

    /**
     * AC 55 + big endian len + cmd + payload + checksum, based on actual
     * user-observed notifications. A Bluetooth notification fragment is not
     * treated as a full frame; sensitive payloads are not copied to logs.
     */
    public static ParsedFrame parseIncoming(byte[] bytes) {
        if (bytes == null || bytes.length < 6 || bytes.length > 256) return null;
        if ((bytes[0] & 0xff) != 0xAC || (bytes[1] & 0xff) != 0x55) return null;
        int len = ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
        if (len < 2 || len != bytes.length - 4) return null;
        int actual = 0;
        for (int i = 4; i < bytes.length - 1; i++) actual = (actual + (bytes[i] & 0xff)) & 0xff;
        int check = bytes[bytes.length - 1] & 0xff;
        byte[] payload = Arrays.copyOfRange(bytes, 5, bytes.length - 1);
        return new ParsedFrame(bytes[4] & 0xff, payload, actual == check);
    }

    public static String outgoingDescription(byte[] bytes) {
        if (!isExactAuthorizedCommand(bytes)) return "不是白名单内指令";
        if (Arrays.equals(bytes, photoThumbnail())) return "已验证BLE拍照 0x22/0x30";
        if (Arrays.equals(bytes, e09BlePhoto())) return "E09 BLE直传照片试验 0x22/0x31";
        if (Arrays.equals(bytes, e09AppLiveAp())) return "E09 RTSP直播AP试验 0x67/0x30";
        if (Arrays.equals(bytes, e09SafeTransferFinish())) return "E09安全结束传输(不删文件) 0x44/0x30/0x01";
        if (Arrays.equals(bytes, wifiP2pInfo())) return "原厂P2P Wi-Fi信息请求 0x39/0x31";
        return "原厂AP Wi-Fi信息请求 0x39/0x30";
    }
}
