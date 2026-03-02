package st.orm.validator

import io.gitlab.arturbosch.detekt.api.Config
import org.junit.jupiter.api.Test

class NoDirectInterpolationRuleSetProviderTest {

    @Test
    fun `provider has correct ruleSetId`() {
        val provider = NoDirectInterpolationRuleSetProvider()
        assert(provider.ruleSetId == "no-direct-interpolation") {
            "Expected ruleSetId 'no-direct-interpolation' but got '${provider.ruleSetId}'."
        }
    }

    @Test
    fun `provider creates RuleSet with NoDirectInterpolationRule`() {
        val provider = NoDirectInterpolationRuleSetProvider()
        val ruleSet = provider.instance(Config.empty)
        assert(ruleSet.rules.isNotEmpty()) { "Expected at least one rule in the RuleSet." }
        assert(ruleSet.rules.any { it is NoDirectInterpolationRule }) {
            "Expected RuleSet to contain NoDirectInterpolationRule."
        }
    }

    @Test
    fun `provider creates RuleSet with matching id`() {
        val provider = NoDirectInterpolationRuleSetProvider()
        val ruleSet = provider.instance(Config.empty)
        assert(ruleSet.id == provider.ruleSetId) {
            "Expected RuleSet id '${provider.ruleSetId}' but got '${ruleSet.id}'."
        }
    }
}
