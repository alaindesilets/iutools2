package org.iutools.morph

class MorphAnalGoldStandard_WordsThatFailedBefore : MorphAnalGoldStandardAbstract() {

    override fun sourceName(): String = "words_that_failed_before"

    override fun initCases() {
        addCase(AnalyzerCase("angilligiaqtunit", arrayOf("{angi:angi/1v}{lli:llik/1vv}{giaq:giaq/1vv}{tu:juq/1vn}{nit:nit/tn-abl-p}")))
        addCase(AnalyzerCase("angilligiaqtittigunnaqpat", arrayOf("{angi:angi/1v}{lli:llik/1vv}{giaq:giaq/1vv}{tit:tit/1vv}{ti:si/1vv}{gunnaq:junnaq/1vv}{pat:vat/tv-int-3p}")))
        addCase(AnalyzerCase("angilligiaqtitsigunnaqpat", arrayOf("{angi:angi/1v}{lli:llik/1vv}{giaq:giaq/1vv}{tit:tit/1vv}{si:si/1vv}{gunnaq:junnaq/1vv}{pat:vat/tv-int-3p}")))
    }
}
