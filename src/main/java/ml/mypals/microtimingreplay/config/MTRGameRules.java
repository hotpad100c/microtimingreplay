package ml.mypals.microtimingreplay.config;

import ml.mypals.microtimingreplay.MicroTimingReplay;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public class MTRGameRules {
        public static final GameRule<Boolean> SKIP_EMPTY_QUEUE = GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.MISC).buildAndRegister(MicroTimingReplay.id("skip_empty_queue"));

        public static final GameRule<Boolean> SKIP_EMPTY_UPDATE = GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.MISC).buildAndRegister(MicroTimingReplay.id("skip_empty_update"));

        public static final GameRule<Boolean> SKIP_EMPTY_PHASE = GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.MISC).buildAndRegister(MicroTimingReplay.id("skip_empty_phase"));

        public static final GameRule<Boolean> SKIP_EMPTY_ENTITY_TICK = GameRuleBuilder.forBoolean(false)
                        .category(GameRuleCategory.MISC).buildAndRegister(MicroTimingReplay.id("skip_empty_entity_tick"));

        public static final GameRule<Boolean> SKIP_EMPTY_BLOCK_ENTITY_TICK = GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.MISC).buildAndRegister(MicroTimingReplay.id("skip_empty_block_entity_tick"));

        public static final GameRule<Boolean> STEP_IGNORE_UPDATES = GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.MISC).buildAndRegister(MicroTimingReplay.id("step_ignore_updates"));

        public static final GameRule<Boolean> STEP_IGNORE_EXITING = GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.MISC).buildAndRegister(MicroTimingReplay.id("step_ignore_exiting"));

        public static final GameRule<Boolean> SKIP_DELTA_CHANGES = GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.MISC).buildAndRegister(MicroTimingReplay.id("skip_delta_changes"));

        public static void init() {
        }
}
