package com.example

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object TemplateMod : ModInitializer {
	const val MOD_ID: String = "handhold"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		HandholdCommands.register()
		HandholdService.registerTickHandler()
		LOGGER.info("Handhold initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
