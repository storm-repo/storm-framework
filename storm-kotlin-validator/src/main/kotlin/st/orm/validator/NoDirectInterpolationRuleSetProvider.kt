package st.orm.validator

import io.gitlab.arturbosch.detekt.api.*

class NoDirectInterpolationRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "no-direct-interpolation"
    override fun instance(config: Config): RuleSet = RuleSet(ruleSetId, listOf(NoDirectInterpolationRule(config)))
}
