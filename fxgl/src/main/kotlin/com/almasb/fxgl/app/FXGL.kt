/*
 * FXGL - JavaFX Game Library. The MIT License (MIT).
 * Copyright (c) AlmasB (almaslvl@gmail.com).
 * See LICENSE for details.
 */

package com.almasb.fxgl.app

import com.almasb.fxgl.asset.AssetLoader
import com.almasb.fxgl.audio.AudioPlayer
import com.almasb.fxgl.core.logging.Logger
import com.almasb.fxgl.event.EventBus
import com.almasb.fxgl.gameplay.Gameplay
import com.almasb.fxgl.io.FS
import com.almasb.fxgl.io.serialization.Bundle
import com.almasb.fxgl.service.impl.display.FXGLDisplay
import com.almasb.fxgl.service.impl.executor.FXGLExecutor
import com.almasb.fxgl.service.impl.net.FXGLNet
import com.almasb.fxgl.time.LocalTimer
import com.almasb.fxgl.time.OfflineTimer
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.name.Named
import com.google.inject.name.Names
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Consumer

/**
 * Represents the entire FXGL infrastructure.
 * Can be used to pass internal properties (key-value pair) around.
 * Can be used for communication between non-related parts.
 * Not to be abused.
 *
 * @author Almas Baimagambetov (AlmasB) (almaslvl@gmail.com)
 */
class FXGL private constructor() {

    companion object {
        private lateinit var internalApp: GameApplication

        private lateinit var internalBundle: Bundle

        private val log = Logger.get("FXGL")

        /**
         * Temporarily holds k-v pairs from system.properties.
         */
        private val internalProperties = Properties()

        private var configured = false

        /**
         * @return FXGL system settings
         */
        @JvmStatic fun getSettings() = internalApp.settings

        /**
         * @return instance of the running game application
         */
        @JvmStatic fun getApp() = internalApp

        @JvmStatic fun getAppWidth() = internalApp.width

        @JvmStatic fun getAppHeight() = internalApp.height

        /**
         * @return instance of the running game application cast to the actual type
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic fun <T : GameApplication> getAppCast() = internalApp as T

        /**
         * Note: the system bundle is saved on exit and loaded on init.
         * This bundle is meant to be used by the FXGL system only.
         * If you want to save global (non-gameplay) data use user profiles instead.
         *
         * @return FXGL system data bundle
         */
        @JvmStatic fun getSystemBundle() = internalBundle

        /**
         * Constructs FXGL.
         */
        @JvmStatic fun configure(appModule: ApplicationModule, vararg modules: Module) {
            if (configured)
                return

            configured = true

            internalApp = appModule.app

            createRequiredDirs()

            val allModules = arrayListOf(*modules)
            allModules.add(buildPropertiesModule())
            allModules.add(appModule)

            injector = Guice.createInjector(allModules)

            if (firstRun)
                loadDefaultSystemData()
            else
                loadSystemData()
        }

        private var firstRun = false

        /**
         * @return true iff FXGL is running for the first time
         * @implNote we actually check if "system/" exists in running dir, so if it was
         *            deleted, then this method also returns true
         */
        @JvmStatic fun isFirstRun() = firstRun

        private fun createRequiredDirs() {

            val systemDir = Paths.get("system/")

            if (!Files.exists(systemDir)) {
                firstRun = true

                Files.createDirectories(systemDir)

                val readmeFile = Paths.get("system/Readme.txt")

                Files.write(readmeFile, "This directory contains FXGL system data files.".lines())
            }
        }

        private fun saveSystemData() {
            log.debug("Saving FXGL system data")

            FS.writeDataTask(internalBundle, "system/fxgl.bundle")
                    .onFailure(Consumer { log.warning("Failed to save: $it") })
                    .execute()
        }

        private fun loadSystemData() {
            log.debug("Loading FXGL system data")

            FS.readDataTask<Bundle>("system/fxgl.bundle")
                    .onSuccess(Consumer {
                        internalBundle = it
                        internalBundle.log()
                    })
                    .onFailure(Consumer {
                        log.warning("Failed to load: $it")
                        loadDefaultSystemData()
                    })
                    .execute()
        }

        private fun loadDefaultSystemData() {
            log.debug("Loading default FXGL system data")

            // populate with default info
            internalBundle = Bundle("FXGL")
            //internalBundle.put("version.check", LocalDate.now())
        }

        /**
         * Destructs FXGL.
         */
        @JvmStatic protected fun destroy() {
            if (!configured)
                throw IllegalStateException("FXGL has not been configured")

            saveSystemData()
        }

        /**
         * Dependency injector.
         */
        private lateinit var injector: Injector

        /**
         * Obtain an instance of a type.
         * It may be expensive to use this in a loop.
         * Store a reference to the instance instead.
         *
         * @param type type
         *
         * @return instance
         */
        // TODO: isolate in reflection utils somewhere
        @JvmStatic fun <T> getInstance(type: Class<T>): T {
            return type.getDeclaredConstructor().newInstance()
        }

        private fun buildPropertiesModule(): Module {
            return object : AbstractModule() {

                override fun configure() {
                    for ((k, v) in internalProperties.intMap)
                        bind(Int::class.java).annotatedWith(k).toInstance(v)

                    for ((k, v) in internalProperties.doubleMap)
                        bind(Double::class.java).annotatedWith(k).toInstance(v)

                    for ((k, v) in internalProperties.booleanMap)
                        bind(Boolean::class.java).annotatedWith(k).toInstance(v)

                    for ((k, v) in internalProperties.stringMap)
                        bind(String::class.java).annotatedWith(k).toInstance(v)

                    internalProperties.clear()
                }
            }
        }

        /* CONVENIENCE ACCESSORS - SERVICES */

        private val _assetLoader by lazy { AssetLoader() }
        @JvmStatic fun getAssetLoader() = _assetLoader

        private val _eventBus by lazy { EventBus() }
        @JvmStatic fun getEventBus() = _eventBus

        private val _audioPlayer by lazy { AudioPlayer() }
        @JvmStatic fun getAudioPlayer() = _audioPlayer

        private val _display by lazy { FXGLDisplay() }
        @JvmStatic fun getDisplay() = _display

        @JvmStatic fun getNotificationService() = getSettings().notificationService

        private val _executor by lazy { FXGLExecutor() }
        @JvmStatic fun getExecutor() = _executor

        private val _net by lazy { FXGLNet() }
        @JvmStatic fun getNet() = _net

        @JvmStatic fun getExceptionHandler() = getSettings().exceptionHandler

        @JvmStatic fun getUIFactory() = getSettings().uiFactory

        private val _gameplay by lazy { Gameplay() }
        @JvmStatic fun getGameplay() = _gameplay

        /* OTHER CONVENIENCE ACCESSORS */

        private val _input by lazy { internalApp.input }
        @JvmStatic fun getInput() = _input

        /**
         * @return new instance on each call
         */
        @JvmStatic fun newLocalTimer() = internalApp.stateMachine.playState.timer.newLocalTimer()

        /**
         * @param name unique name for timer
         * @return new instance on each call
         */
        @JvmStatic fun newOfflineTimer(name: String): LocalTimer = OfflineTimer(name)

        private val _masterTimer by lazy { internalApp.masterTimer }
        @JvmStatic fun getMasterTimer() = _masterTimer

        /**
         * Get value of an int property.

         * @param key property key
         * *
         * @return int value
         */
        @JvmStatic fun getInt(key: String) = Integer.parseInt(getProperty(key))

        /**
         * Get value of a double property.

         * @param key property key
         * *
         * @return double value
         */
        @JvmStatic fun getDouble(key: String) = java.lang.Double.parseDouble(getProperty(key))

        /**
         * Get value of a boolean property.

         * @param key property key
         * *
         * @return boolean value
         */
        @JvmStatic fun getBoolean(key: String) = java.lang.Boolean.parseBoolean(getProperty(key))

        /**
         * @param key property key
         * @return string value
         */
        @JvmStatic fun getString(key: String) = getProperty(key)

        /**
         * @param key property key
         * @return property value
         */
        private fun getProperty(key: String) = System.getProperty("FXGL.$key")
                ?: throw IllegalArgumentException("Key \"$key\" not found!")

        /**
         * Set an int, double, boolean or String property.
         * The value can then be retrieved with FXGL.get* methods.
         *
         * @param key property key
         * @param value property value
         */
        @JvmStatic fun setProperty(key: String, value: Any) {
            System.setProperty("FXGL.$key", value.toString())

            if (!configured) {

                if (value == "true" || value == "false") {
                    internalProperties.booleanMap[Names.named(key)] = java.lang.Boolean.parseBoolean(value as String)
                } else {
                    try {
                        internalProperties.intMap[Names.named(key)] = Integer.parseInt(value.toString())
                    } catch(e: Exception) {
                        try {
                            internalProperties.doubleMap[Names.named(key)] = java.lang.Double.parseDouble(value.toString())
                        } catch(e: Exception) {
                            internalProperties.stringMap[Names.named(key)] = value.toString()
                        }
                    }
                }
            }
        }
    }

    private class Properties {
        val intMap = hashMapOf<Named, Int>()
        val doubleMap = hashMapOf<Named, Double>()
        val booleanMap = hashMapOf<Named, Boolean>()
        val stringMap = hashMapOf<Named, String>()

        fun clear() {
            intMap.clear()
            doubleMap.clear()
            booleanMap.clear()
            stringMap.clear()
        }
    }
}