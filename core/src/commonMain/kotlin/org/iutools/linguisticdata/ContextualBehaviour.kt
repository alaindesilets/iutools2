package org.iutools.linguisticdata

/*
 * context: last character of stem: V (any vowel), t, k, q
 * basicForm: surface of the affix for this context
 * action1: action of the affix on (last character of) stem in this context;
 *          in the vowel context, it must be only one vowel, not two
 * action2: action of the affix in this context in case the stem (after
 *          applying the action1) ends with 2 vowels
 */
class ContextualBehaviour(
    var context: Char,
    val basicForm: String,
    val action1: Action,
    val action2: Action?
) {
    /**
     * The forms of an affix depend on the context and on the action(s) in that context.
     * An affix may have a different form in different contexts.
     * In the case of a consonantal context where the first action is deletion of
     * the stem's final consonant resulting in a stem ending with 2 vowels, and
     * in the case of a vowel context where the stem's ends with 2 vowels,
     * the second action may have an effect on the surface form of the affix,
     * for example by inserting one or two characters.
     * @return A List of [surfaceForm, endOfStem] pairs
     */
    fun formsInContext(): List<Array<String>> {
        // eg. allak/1vv in 'k' context 1) deletes 'k' and 2) inserts 'ra' if VV
        //   1. get the canonical of the affix in this context; this will normally be the surface form in that context : allak
        //   2. apply action 1 (may modify the default surface form, like insertion): allak
        //   3. apply action 2 (may modify the form resulting of action1 for cases of VV stems)
        val formAndEndOfStemInContext = mutableListOf<Array<String>>()
        val formAndEndOfStemInContextAfterAction1 = action1.formAndEndOfStemInContext(basicForm, context, 1)
        if (formAndEndOfStemInContextAfterAction1 != null) {
            formAndEndOfStemInContext.add(formAndEndOfStemInContextAfterAction1)
        }
        if (action2 != null && action2.type != Action.NULLACTION) {
            val formAndEndOfStemInContextAfterAction2 =
                action2.formAndEndOfStemInContext(formAndEndOfStemInContextAfterAction1!![0], context, 2)
            if (formAndEndOfStemInContextAfterAction2 != null) {
                formAndEndOfStemInContext.add(formAndEndOfStemInContextAfterAction2)
            }
        }
        return formAndEndOfStemInContext
    }
}
