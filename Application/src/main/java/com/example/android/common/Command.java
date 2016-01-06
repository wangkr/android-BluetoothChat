package com.example.android.common;

/**
 * Created by Kairong on 2015/12/29.
 * mail:wangkrhust@gmail.com
 */
public class Command {
    // commands
    private static final byte[][] commands = new byte[][] {
            /*空命令*/
            {'P', 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
            /*获取锁具ID*/
            {'P', 0x01, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
            /*擦除锁具固件*/
            {'P', 0x01, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
            /*发送下载锁具命令*/
            {'P', 0x01, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
            /*发送验证新锁具命令*/
            {'P', 0x01, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}
    };

    public enum CommandType{
        NONE(0),
        GET_ID(1),
        ERASE_GJ(2),
        DOWN_GJ(3),
        VERIFY_GJ(4);
        private int value;
        CommandType(int value) {
            this.value = value;
        }
    }

    public static byte[] getCmd(CommandType commandType) {
        return commands[commandType.value];
    }

    public static boolean isOK(CommandType commandType, byte[] data) {
        if (data.length < 5) {
            return false;
        }

        byte[] cmd = getCmd(commandType);
        if (data[0] != 'L') {
            return false;
        }

        for (int i = 1; i < 4; i++) {
            if (cmd[i] != data[i]) {
                return false;
            }
        }

        return (0xFF & data[4]) == 0;
    }

    public static int getDataFrameNum(CommandType commandTypes, byte[] data) {
        if (data.length < 5) {
            return -1;
        }

        if (data[0] != 'L') {
            return -1;
        }

        return 0xFF & data[1];
    }

    public static void attachLengthInfo(byte[] cmd, int total, int curPage) {
        if (cmd.length < 8) {
            return;
        }
        byte cur1, cur2, total1, total2;
        cur2 = (byte)((curPage&0x0000FF00) >> 8);
        cur1 = (byte)((curPage&0x000000FF));
        total2 = (byte)((total&0x0000FF00) >> 8);
        total1 = (byte)((total&0x000000FF));
        cmd[4] = cur1;cmd[5] = cur2;
        cmd[6] = total1;cmd[7] = total2;
    }
}
