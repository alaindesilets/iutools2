package org.iutools.linguisticdata

/*
 * SurfaceFormOfAffix est la classe des objets qui sont enregistrés
 * dans les MorceauAffixe d'une DecompositionState.
 */
class SurfaceFormOfAffix(
    val form: String,
    val key: String?,
    val uniqueId: String,
    val type: String?,
    val context: String?,
    val action1: Action?,
    val action2: Action?
) {
    @Throws(LinguisticDataException::class)
    fun getAffix(): Affix? = LinguisticData.getInstance().getAffixWithId(uniqueId)

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[SurfaceFormOfAffix:")
        sb.append("\nform: $form")
        sb.append("\ntype: $type")
        sb.append("\nkey: $key")
        sb.append("\ncontext: $context")
        sb.append("\naction1: ${action1.toString()}")
        sb.append("\naction2: $action2")
        sb.append("]")
        return sb.toString()
    }
}
