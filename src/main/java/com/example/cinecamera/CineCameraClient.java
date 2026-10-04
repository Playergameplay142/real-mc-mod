package com.example.cinecamera;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CineCameraClient implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("cinecamera");

	@Override
	public void onInitializeClient() {
		LOG.info("CineCamera yuklendi. F5: 1. sahis -> omuz ustu 3. sahis -> 2. sahis (onden).");
	}
}
