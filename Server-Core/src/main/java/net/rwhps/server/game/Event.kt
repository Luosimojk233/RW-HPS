/*
 * Copyright 2020-2024 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.game

import com.corrodinggames.rts.gameFramework.j.c
import net.rwhps.server.core.thread.CallTimeTask
import net.rwhps.server.core.thread.Threads
import net.rwhps.server.data.global.Data
import net.rwhps.server.game.event.core.EventListenerHost
import net.rwhps.server.game.event.game.*
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.net.Administration.PlayerInfo
import net.rwhps.server.plugin.internal.headless.inject.core.GameEngine
import net.rwhps.server.util.Time.millis
import net.rwhps.server.util.annotations.core.EventListenerHandler
import net.rwhps.server.util.inline.coverConnect
import net.rwhps.server.util.log.Log
import net.rwhps.server.util.log.Log.error
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * @author Dr (dr@der.kim)
 */
@Suppress("UNUSED", "UNUSED_PARAMETER")
class Event: EventListenerHost {
    @EventListenerHandler
    fun registerServerHessStartPort(serverHessStartPort: ServerHessStartPort) {
        HeadlessModuleManage.hps.gameLinkServerData.maxUnit = Data.configServer.maxUnit
        HeadlessModuleManage.hps.gameLinkServerData.income = Data.configServer.defIncome

        if (Data.config.autoUpList) {
            Data.SERVER_COMMAND.handleMessage("uplist add", Data.defPrint)
        }
    }

    @EventListenerHandler
    fun registerPlayerJoinEvent(playerJoinEvent: PlayerJoinEvent) {
        val player = playerJoinEvent.player
        if (player.name.isBlank() || player.name.length > 30) {
            player.kickPlayer(player.getinput("kick.name.failed"))
            return
        }

        if (Data.core.admin.bannedUUIDs.contains(player.connectHexID)) {
            try {
                player.kickPlayer(player.i18NBundle.getinput("kick.ban"))
            } catch (ioException: IOException) {
                error("[Player] Send Kick Player Error", ioException)
            }
            return
        }

        if (Data.core.admin.playerDataCache.containsKey(player.connectHexID)) {
            val info = Data.core.admin.playerDataCache[player.connectHexID]!!
            if (info.timesKicked > millis()) {
                try {
                    player.kickPlayer(player.i18NBundle.getinput("kick.you.time"))
                } catch (ioException: IOException) {
                    error("[Player] Send Kick Player Error", ioException)
                }
                return
            } else {
                player.muteTime = info.timeMute
            }
        }

        HeadlessModuleManage.hps.room.call.sendSystemMessage(Data.i18NBundle.getinput("player.ent", player.name))
        Log.clog("&c" + Data.i18NBundle.getinput("player.ent", player.name))

        if (Data.configServer.autoStartMinPlayerSize != -1 && HeadlessModuleManage.hps.room.playerManage.playerGroup.size >= Data.configServer.autoStartMinPlayerSize && !Threads.containsTimeTask(
                    CallTimeTask.AutoStartTask
            )) {
            var flagCount = 60
            Threads.newTimedTask(CallTimeTask.AutoStartTask, 0, 1, TimeUnit.SECONDS) {
                if (HeadlessModuleManage.hps.room.isStartGame) {
                    Threads.closeTimeTask(CallTimeTask.AutoStartTask)
                    return@newTimedTask
                }

                flagCount--

                if (flagCount > 0) {
                    if ((flagCount - 5) > 0) {
                        HeadlessModuleManage.hps.room.call.sendSystemMessage(Data.i18NBundle.getinput("auto.start", flagCount))
                    }
                    return@newTimedTask
                }

                Threads.closeTimeTask(CallTimeTask.AutoStartTask)
                Threads.closeTimeTask(CallTimeTask.PlayerAfkTask)

                HeadlessModuleManage.hps.room.clientHandler.handleMessage("start", null)
            }
        }

        if (Data.configServer.enterAd.isNotBlank()) {
            player.sendSystemMessage(Data.configServer.enterAd)
        }
        // ConnectServer("127.0.0.1",5124,player.con)

        if (Data.neverEnd) {
            thread {
                player.sendSystemMessage("需要等待五秒钟生成单位")
                player.team = player.index
                GameEngine.netEngine.e(null as c?)
                Thread.sleep(5000)
                if (player.con != null) {
                    val map = HeadlessModuleManage.hps.gameFunction.neverEnd
                    val width = (Random.nextInt(0, Int.MAX_VALUE) % map[0]) * 20.toFloat() + Random.nextFloat()
                    val height = (Random.nextInt(0, Int.MAX_VALUE) % map[1]) * 20.toFloat() + Random.nextFloat()
                    //player.con!!.gameSummon("modularSpider", width, height)
                    player.con!!.gameSummon("combatEngineer", width, height)
                    player.never = true
                }
            }
        }
    }

    @EventListenerHandler
    fun registerPlayerLeaveEvent(playerLeaveEvent: PlayerLeaveEvent) {
        val player = playerLeaveEvent.player
        if (Data.configServer.oneAdmin && player.isAdmin && player.autoAdmin && HeadlessModuleManage.hps.room.playerManage.playerGroup.size > 0) {
            HeadlessModuleManage.hps.room.playerManage.playerGroup.eachFind({ !it.isAdmin && !it.isAi && !Data.neverEnd }) {
                it.isAdmin = true
                it.autoAdmin = true
                player.isAdmin = false
                player.autoAdmin = false
                HeadlessModuleManage.hps.room.call.sendSystemMessage("give.ok", it.name)
            }
        }

        Data.core.admin.playerDataCache[player.connectHexID] = PlayerInfo(player.connectHexID, player.kickTime, player.muteTime)

        if (HeadlessModuleManage.hps.room.isStartGame) {
            HeadlessModuleManage.hps.room.call.sendSystemMessage("player.dis", player.name)
        } else {
            HeadlessModuleManage.hps.room.call.sendSystemMessage("player.disNoStart", player.name)
        }
        Log.clog("&c" + Data.i18NBundle.getinput("player.dis", player.name))

        if (Data.configServer.autoStartMinPlayerSize != -1 && HeadlessModuleManage.hps.room.playerManage.playerGroup.size <= Data.configServer.autoStartMinPlayerSize && Threads.containsTimeTask(
                    CallTimeTask.AutoStartTask
            )) {
            Threads.closeTimeTask(CallTimeTask.AutoStartTask)
        }
    }

    @EventListenerHandler
    fun registerGameStartEvent(serverGameStartEvent: ServerGameStartEvent) {
        Data.core.admin.playerDataCache.clear()

        if (Data.configServer.startAd.isNotBlank()) {
            HeadlessModuleManage.hps.room.call.sendSystemMessage(Data.configServer.startAd)
        }

        Log.clog("[Start New Game]")
    }

    @EventListenerHandler
    fun registerGameOverEvent(serverGameOverEvent: ServerGameOverEvent) {
        System.gc()
    }

    @EventListenerHandler
    fun registerPlayerBanEvent(serverBanEvent: PlayerBanEvent) {
        val player = serverBanEvent.player
        Data.core.admin.bannedUUIDs.add(player.connectHexID)
        Data.core.admin.bannedIPs.add(player.con!!.coverConnect().ip)
        try {
            player.kickPlayer(player.i18NBundle.getinput("kick.ban"))
        } catch (ioException: IOException) {
            error("[Player] Send Kick Player Error", ioException)
        }
        HeadlessModuleManage.hps.room.call.sendSystemMessage("ban.yes", player.name)
    }

    @EventListenerHandler
    fun registerPlayerIpBanEvent(serverIpBanEvent: PlayerIpBanEvent) {
        val player = serverIpBanEvent.player
        Data.core.admin.bannedIPs.add(player.con!!.coverConnect().ip)
        try {
            player.kickPlayer(player.i18NBundle.getinput("kick.ban"))
        } catch (ioException: IOException) {
            error("[Player] Send Kick Player Error", ioException)
        }
        HeadlessModuleManage.hps.room.call.sendSystemMessage("ban.yes", player.name)
    }
}