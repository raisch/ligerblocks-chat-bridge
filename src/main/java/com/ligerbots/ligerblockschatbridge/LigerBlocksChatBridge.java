package com.ligerbots.ligerblockschatbridge;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(LigerBlocksChatBridge.MOD_ID)
public class LigerBlocksChatBridge {
    public static final String MOD_ID = "ligerblocks_chat_bridge";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LigerBlocksChatBridge() {
        LOGGER.info("LigerBlocks Chat Bridge loaded");
    }
}

