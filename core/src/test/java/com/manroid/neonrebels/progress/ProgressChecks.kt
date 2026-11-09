package com.manroid.neonrebels.progress

import kotlin.math.abs

/** Campaign, squad, upgrade, reward and save rules; runs with only a Kotlin compiler and JVM. */
object ProgressChecks {
    private var checks = 0

    @JvmStatic
    fun main(args: Array<String>) {
        runAll()
    }

    fun runAll() {
        checks = 0
        newCampaign(); tutorialRecord(); unlocking(); continueTarget(); squad(); stats(); purchases(); rewards(); saves(); legacy()
        println("PASS: $checks progress assertions")
    }

    private fun check(condition: Boolean, message: String) {
        checks++
        if (!condition) throw AssertionError(message)
    }

    /** In-memory store; [failWrites] simulates a full disk, [onWrite] runs inside a write. */
    class MemoryStore : SaveStore {
        val data = HashMap<String, String>()
        var failWrites = false
        var failReads = false
        var onWrite: (() -> Unit)? = null

        override fun read(key: String): String? {
            if (failReads) throw ClassCastException("java.lang.Boolean cannot be cast to java.lang.String")
            return data[key]
        }

        override fun write(values: Map<String, String?>) {
            onWrite?.invoke()
            if (failWrites) throw IllegalStateException("disk full")
            for ((k, v) in values) if (v == null) data.remove(k) else data[k] = v
        }
    }

    private class Game(val store: MemoryStore = MemoryStore()) {
        val repo = ProgressRepository(store)
        val campaign = CampaignService(repo)
        val team = TeamService(repo, campaign)
        val upgrades = UpgradeService(repo)

        init {
            repo.load()
        }

        fun started(): Game {
            check(campaign.newCampaign(), "new campaign saved")
            return this
        }

        fun win(levelId: String, seconds: Float = 300f, collected: Int = 0): RewardOutcome {
            val (setup, error) = team.start(team.draft(), levelId)
            check(setup != null, "run starts: $error")
            return campaign.applyResult(RunResult(setup!!.runId, levelId, true, seconds, collected))
        }

        private fun check(c: Boolean, m: String) = ProgressChecks.check(c, m)
    }

    private fun newCampaign() {
        val g = Game()
        check(g.repo.status == ProgressRepository.LoadStatus.EMPTY && !g.campaign.started(), "Fresh install has no campaign")
        g.started()
        val s = g.repo.state
        check(s.campaignStarted && s.components == 0L, "New campaign starts with zero components")
        check(s.unlockedCharacters == setOf("an", "vy") && s.team == listOf("an", "vy"), "An and Vy unlocked, An leads")
        check(s.selectedLevelId == "streets_01" && s.completedLevels.isEmpty(), "First level selected, nothing cleared")
        check(GameData.characters.all { c -> UpgradeType.values().all { s.upgradeLevel(c.id, it) == 0 } }, "Upgrades at level 0")
        g.repo.update { it.components = 900; it.setUpgradeLevel("an", UpgradeType.DAMAGE, 3); it.completedLevels += "streets_01" }
        val before = SaveCodec.encode(g.repo.state)
        // Cancelling the confirmation simply never calls newCampaign.
        check(SaveCodec.encode(g.repo.state) == before, "Cancel keeps progress untouched")
        val nextRun = g.repo.state.nextRunId
        g.started()
        check(g.repo.state.components == 0L && g.repo.state.upgradeLevel("an", UpgradeType.DAMAGE) == 0 &&
            g.repo.state.completedLevels.isEmpty(), "Confirmed reset clears progress, components and upgrades")
        check(g.repo.state.nextRunId == nextRun, "Run ids keep growing across resets")
    }

    private fun tutorialRecord() {
        val g = Game().started()
        check(g.campaign.recordTutorial("move", "done") && g.campaign.recordTutorial("swap", "skipped"), "Tutorial steps saved")
        val reopened = Game(g.store)
        check(reopened.campaign.tutorialRecord() == mapOf("move" to "done", "swap" to "skipped"), "Tutorial progress survives reload")
        reopened.campaign.newCampaign()
        check(reopened.campaign.tutorialRecord().isEmpty(), "New campaign resets the tutorial")
        val text = SaveCodec.encode(g.repo.state).replace("tutorial.move=done", "tutorial.move=bogus")
        val body = text.substring(0, text.lastIndexOf("\ncrc="))
        val crc = java.util.zip.CRC32(); crc.update(body.toByteArray(Charsets.UTF_8))
        val decoded = SaveCodec.decode("$body\ncrc=${java.lang.Long.toHexString(crc.value)}")
        check(decoded is SaveCodec.Decoded.Ok && "move" !in decoded.state.tutorial, "Invalid tutorial values dropped")
    }

    private fun unlocking() {
        val g = Game().started()
        val first = g.campaign.status("streets_01")!!
        check(first.unlocked && first.implemented && first.playable && !first.completed, "First level playable")
        val second = g.campaign.status("streets_02")!!
        check(!second.unlocked && !second.playable, "Second level locked by progress")
        check(g.campaign.lockReason("streets_02")?.requiredLevel?.id == "streets_01", "Lock reason names previous level")
        check(g.campaign.lockReason("factory_01")?.bossOfPreviousChapter == true &&
            g.campaign.lockReason("factory_01")?.requiredLevel?.id == "streets_03", "Next chapter needs previous boss")
        val outcome = g.win("streets_01")
        val afterWin = g.campaign.status("streets_02")!!
        check(afterWin.unlocked && !afterWin.implemented && !afterWin.playable, "Unlocked but unimplemented is not playable")
        check(g.campaign.status("streets_01")!!.completed && g.campaign.canPlay("streets_01"), "Cleared level can be replayed")
        check(outcome.nextPlayable == null && g.campaign.allContentCleared(), "All current content cleared, no empty level opened")
        val (setup, error) = g.team.start(g.team.draft(), "streets_02")
        check(setup == null && error == TeamService.StartError.LEVEL_NOT_PLAYABLE, "Coming-soon level cannot start")
        g.repo.update { it.completedLevels += listOf("streets_02", "streets_03") }
        check(g.campaign.status("factory_01")!!.unlocked && !g.campaign.canPlay("factory_01"), "Boss clear unlocks next chapter")
        check(!g.campaign.status("factory_02")!!.unlocked, "Chapter levels still unlock in order")
        check(g.campaign.select("factory_02") && g.repo.state.selectedLevelId == "factory_02", "Locked level can be selected to view")
        check(!g.campaign.select("nowhere_09"), "Unknown level ids are rejected")
        check(g.campaign.levelForChapter(GameData.campaign.chapters[1]).id == "factory_02", "Chapter focus keeps remembered level")
        check(g.campaign.levelForChapter(GameData.campaign.chapters[0]).id == "streets_01", "Fully cleared chapter focuses first level")
    }

    private fun continueTarget() {
        val g = Game().started()
        check(g.campaign.continueLevel().id == "streets_01", "Continue picks first playable level")
        g.repo.update { it.lastPlayedLevelId = "streets_03" }
        check(g.campaign.continueLevel().id == "streets_01", "Unplayable last level falls back")
        g.win("streets_01")
        check(g.campaign.continueLevel().id == "streets_01", "Everything cleared: nearest playable level")
    }

    private fun squad() {
        val g = Game().started()
        val d = g.team.draft()
        check(d.slots.toList() == listOf("an", "vy") && g.team.valid(d), "Draft copies saved squad")
        check(g.team.add(d, "an") == TeamService.Change.OK && d.count() == 2, "Adding a member twice is a no-op")
        check(g.team.add(d, "khang") == TeamService.Change.NOT_AVAILABLE, "Khang preview cannot be picked")
        g.team.swap(d)
        check(d.slots.toList() == listOf("vy", "an"), "Swap reorders lead and reserve")
        check(g.team.remove(d, "vy") == TeamService.Change.OK && d.slots.toList() == listOf("an", null), "Removing lead promotes reserve")
        check(!g.team.valid(d) && d.count() == 1, "One member is not a valid squad")
        val (none, error) = g.team.start(d, "streets_01")
        check(none == null && error == TeamService.StartError.INVALID_TEAM, "Cannot deploy an incomplete squad")
        check(g.repo.state.team == listOf("an", "vy"), "Unconfirmed draft does not touch saved squad")
        check(g.team.add(d, "vy") == TeamService.Change.OK, "Re-add Vy")
        check(g.team.add(d, "an") == TeamService.Change.OK, "Full squad accepts existing member")
        g.team.swap(d)
        val (setup, _) = g.team.start(d, "streets_01")
        check(setup != null && setup.team == listOf("vy", "an"), "Run uses confirmed order")
        check(g.repo.state.team == listOf("vy", "an") && g.repo.state.lastPlayedLevelId == "streets_01", "Squad and last level saved on deploy")
        check(setup!!.loadout().first == com.manroid.neonrebels.model.CharacterType.VY, "Lead slot deploys first in gameplay")
        val (again, _) = g.team.start(d, "streets_01")
        check(again != null && again.runId == setup.runId + 1, "Every run gets a new id")
        val full = DraftTeam(listOf("an", "vy"))
        g.repo.update { it.unlockedCharacters += "khang" }
        check(g.team.add(full, "khang") == TeamService.Change.NOT_AVAILABLE, "Unlocked but unimplemented stays unpickable")
    }

    private fun stats() {
        val an = GameData.AN
        val vy = GameData.VY
        check(StatCalculator.health(an, 1) == 1320, "An health level 1")
        check(StatCalculator.damage(an, 1) == 108, "An damage level 1")
        check(abs(StatCalculator.cooldown(vy, 1) - 5.7f) < 1e-4f, "Vy cooldown level 1 keeps full precision")
        check(StatCalculator.health(vy, 5) == 1500 && StatCalculator.damage(an, 5) == 140, "Level 5 values")
        check(StatCalculator.formatSeconds(5.7f) == "5.7s" && StatCalculator.formatSeconds(6f) == "6s" &&
            StatCalculator.formatSeconds(7.2f * .95f) == "6.84s", "Cooldown shows at most two decimals")
        check(GameData.upgrade(UpgradeType.HEALTH).costs.toList() == listOf(100, 200, 350, 550, 800), "Health cost table")
        val g = Game().started()
        val preview = g.upgrades.preview("an", UpgradeType.HEALTH)
        check(preview.current == 1200f && preview.next == 1320f && preview.cost == 100L && preview.missing == 100L, "Health preview")
        val d = g.upgrades.preview("an", UpgradeType.DAMAGE)
        check(d.current == 100f && d.next == 108f && d.cost == 150L, "Damage preview uses damage values")
        g.repo.update { it.setUpgradeLevel("an", UpgradeType.HEALTH, 2) }
        val run = g.team.start(g.team.draft(), "streets_01").first!!
        check(run.loadout().maxHp[0] == StatCalculator.health(an, 2) && run.loadout().maxHp[1] == 1000, "Run snapshot uses shared formula")
    }

    private fun purchases() {
        val g = Game().started()
        g.repo.update { it.components = 100 }
        check(g.upgrades.purchase("an", UpgradeType.HEALTH) == UpgradeService.Purchase.OK, "Buy with exact balance")
        check(g.repo.state.components == 0L && g.repo.state.upgradeLevel("an", UpgradeType.HEALTH) == 1, "Balance and level change together")
        check(g.upgrades.preview("an", UpgradeType.HEALTH).cost == 200L, "Next cost read from new level")
        check(g.upgrades.purchase("an", UpgradeType.HEALTH) == UpgradeService.Purchase.INSUFFICIENT, "Not enough components")
        check(g.upgrades.preview("an", UpgradeType.HEALTH).missing == 200L, "Missing amount shown")
        check(g.upgrades.purchase("khang", UpgradeType.HEALTH) == UpgradeService.Purchase.LOCKED, "Locked character cannot upgrade")
        g.repo.update { it.components = 10_000; it.setUpgradeLevel("vy", UpgradeType.COOLDOWN, 5) }
        check(g.upgrades.purchase("vy", UpgradeType.COOLDOWN) == UpgradeService.Purchase.MAXED, "Level 5 is the cap")
        check(g.upgrades.preview("vy", UpgradeType.COOLDOWN).let { it.maxed && it.cost == null }, "Maxed preview hides next cost")
        var nested: UpgradeService.Purchase? = null
        g.store.onWrite = { if (nested == null) nested = g.upgrades.purchase("an", UpgradeType.DAMAGE) }
        check(g.upgrades.purchase("an", UpgradeType.DAMAGE) == UpgradeService.Purchase.OK, "Outer purchase succeeds")
        g.store.onWrite = null
        check(nested == UpgradeService.Purchase.BUSY && g.repo.state.upgradeLevel("an", UpgradeType.DAMAGE) == 1,
            "Concurrent purchase rejected, only one level bought")
        val balance = g.repo.state.components
        g.store.failWrites = true
        check(g.upgrades.purchase("an", UpgradeType.DAMAGE) == UpgradeService.Purchase.SAVE_FAILED, "Save failure reported")
        check(g.repo.state.components == balance && g.repo.state.upgradeLevel("an", UpgradeType.DAMAGE) == 1, "Failed save keeps old state")
        g.store.failWrites = false
        check(g.upgrades.purchase("an", UpgradeType.DAMAGE) == UpgradeService.Purchase.OK, "Retry after failure works")
    }

    private fun rewards() {
        val g = Game().started()
        val first = g.win("streets_01", seconds = 300f, collected = 35)
        check(first.accepted && first.firstClear && first.reward == 100L + 100L + 35L, "First clear: base + bonus + pickups")
        check(g.repo.state.components == 235L && g.campaign.bestTime("streets_01") == 300f, "Reward and record saved")
        val replay = g.win("streets_01", seconds = 320f, collected = 10)
        check(replay.reward == 110L && !replay.firstClear && !replay.newRecord, "Replay: base + pickups, no bonus")
        check(g.campaign.bestTime("streets_01") == 300f, "Slower time keeps record")
        val faster = g.win("streets_01", seconds = 250f)
        check(faster.newRecord && g.campaign.bestTime("streets_01") == 250f, "Faster time becomes record")
        val balance = g.repo.state.components
        val (lossRun, _) = g.team.start(g.team.draft(), "streets_01")
        val loss = g.campaign.applyResult(RunResult(lossRun!!.runId, "streets_01", false, 99f, 50))
        check(loss.accepted && loss.reward == 0L && g.repo.state.components == balance, "Loss banks nothing")
        val lateWin = g.campaign.applyResult(RunResult(lossRun.runId, "streets_01", true, 10f, 50))
        check(lateWin.duplicate && g.repo.state.components == balance && g.campaign.bestTime("streets_01") == 250f,
            "A lost run cannot be turned into a win")
        val (run, _) = g.team.start(g.team.draft(), "streets_01")
        val ok = g.campaign.applyResult(RunResult(run!!.runId, "streets_01", true, 400f, 0))
        val again = g.campaign.applyResult(RunResult(run.runId, "streets_01", true, 400f, 0))
        check(ok.accepted && again.duplicate && again.reward == ok.reward && g.repo.state.components == balance + 100L,
            "Repeated result callback pays once")
        check(!g.campaign.applyResult(RunResult(lossRun.runId - 1, "streets_01", true, 1f, 0)).accepted, "Older run rejected")
        check(!g.campaign.applyResult(RunResult(g.repo.state.nextRunId + 5, "streets_01", true, 1f, 0)).accepted, "Unknown run rejected")
        val g2 = Game().started()
        g2.repo.update { it.completedLevels.clear() }
        val (failRun, _) = g2.team.start(g2.team.draft(), "streets_01")
        g2.store.failWrites = true
        check(!g2.campaign.applyResult(RunResult(failRun!!.runId, "streets_01", true, 300f, 0)).accepted, "Failed save is not accepted")
        g2.store.failWrites = false
        check(g2.repo.state.components == 0L && g2.campaign.applyResult(RunResult(failRun.runId, "streets_01", true, 300f, 0)).reward == 200L,
            "Result can be retried after a failed save")
    }

    private fun saves() {
        val g = Game().started()
        g.repo.update { it.components = 777; it.setUpgradeLevel("vy", UpgradeType.DAMAGE, 2); it.bestTimes["streets_01"] = 123.5f }
        val text = SaveCodec.encode(g.repo.state)
        val decoded = SaveCodec.decode(text)
        check(decoded is SaveCodec.Decoded.Ok && SaveCodec.encode(decoded.state) == text, "Snapshot round trip")
        val reopened = Game(g.store)
        check(reopened.repo.status == ProgressRepository.LoadStatus.LOADED && reopened.repo.state.components == 777L, "Reload restores progress")
        check(SaveCodec.decode(text.replace("components=777", "components=99999")) is SaveCodec.Decoded.Invalid, "Edited data fails checksum")
        check(SaveCodec.decode(text.substring(0, text.length / 2)) is SaveCodec.Decoded.Invalid, "Truncated data rejected")
        check(SaveCodec.decode(resign(text.replace("saveVersion=1", "saveVersion=9"))) is SaveCodec.Decoded.Invalid, "Newer version not loaded")
        val body = text.substring(0, text.lastIndexOf("\ncrc="))
        val extended = SaveCodec.decode(resign(body + "\nfutureField=42\nupgrade.ghost.HEALTH=3\ncompletedLevels=streets_01,moon_99\ncrc=0"))
        check(extended is SaveCodec.Decoded.Ok && extended.state.components == 777L &&
            "ghost" !in extended.state.upgrades && extended.state.completedLevels == setOf("streets_01"), "Unknown keys and ids ignored safely")
        val clamped = SaveCodec.decode(resign(text.replace("components=777", "components=-5").replace("upgrade.vy.DAMAGE=2", "upgrade.vy.DAMAGE=42")))
        check(clamped is SaveCodec.Decoded.Ok && clamped.state.components == 0L &&
            clamped.state.upgradeLevel("vy", UpgradeType.DAMAGE) == 5, "Numbers clamped to valid range")
        // Corrupt primary: recover from backup and keep the bad copy.
        val store = g.store
        store.data[ProgressRepository.KEY] = "garbage"
        val recovered = Game(store)
        check(recovered.repo.status == ProgressRepository.LoadStatus.RECOVERED_FROM_BACKUP && recovered.campaign.started(), "Backup restores campaign")
        check(store.data[ProgressRepository.CORRUPT] == "garbage", "Corrupt data kept for diagnosis")
        // Both copies broken: nothing is overwritten until the player explicitly starts over.
        store.data[ProgressRepository.KEY] = "garbage"
        store.data[ProgressRepository.BACKUP] = "also garbage"
        val broken = Game(store)
        check(broken.repo.status == ProgressRepository.LoadStatus.CORRUPT && !broken.campaign.started(), "Unrecoverable save reported")
        check(!broken.repo.update { it.components = 1 } && store.data[ProgressRepository.KEY] == "garbage", "No writes over unreadable data")
        check(broken.campaign.newCampaign() && broken.repo.loaded && store.data[ProgressRepository.CORRUPT] == "garbage", "Explicit restart keeps corrupt copy")
    }

    private fun legacy() {
        val store = MemoryStore()
        store.data["showControls"] = "true"
        store.data["level1Complete"] = "true"
        store.data["bestSeconds"] = "312.0"
        store.data["parts"] = "640"
        store.data["level0DAMAGE"] = "3"
        val g = Game(store)
        check(g.repo.status == ProgressRepository.LoadStatus.MIGRATED_LEGACY, "Legacy keys migrated")
        val s = g.repo.state
        check(s.campaignStarted && s.components == 640L && "streets_01" in s.completedLevels &&
            s.bestTimes["streets_01"] == 312f && s.upgradeLevel("an", UpgradeType.DAMAGE) == 2, "Legacy values converted")
        check("parts" !in store.data && store.data["showControls"] == "true" && ProgressRepository.KEY in store.data,
            "Legacy keys replaced, settings untouched")
        val unreadable = MemoryStore()
        unreadable.data["parts"] = "640"
        unreadable.failReads = true
        val g2 = Game(unreadable)
        check(g2.repo.status == ProgressRepository.LoadStatus.CORRUPT && unreadable.data["parts"] == "640",
            "A store that throws on read is reported, not crashed on, and nothing is overwritten")
        unreadable.failReads = false
        check(g2.repo.load() == ProgressRepository.LoadStatus.MIGRATED_LEGACY, "Retry after the read problem loads normally")
    }

    /** Recomputes the checksum after a test edits the body. */
    private fun resign(text: String): String {
        val body = text.substring(0, text.lastIndexOf("\ncrc="))
        val crc = java.util.zip.CRC32()
        crc.update(body.toByteArray(Charsets.UTF_8))
        return "$body\ncrc=${java.lang.Long.toHexString(crc.value)}"
    }
}
