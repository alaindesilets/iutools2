package org.iutools.morph

class MorphAnalGoldStandard_Hansard : MorphAnalGoldStandardAbstract() {

    override fun initCases() {
        addCase(AnalyzerCase("Haammalakkut", arrayOf("{Haammala:Haammalat/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("Hamlakkut", arrayOf("{Hamla:Hamlat/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("Hanta", arrayOf("[decomposition:/Hanta(Hanta)/]"))
                .isProperName());
        addCase(AnalyzerCase("Haviujaq", arrayOf("[decomposition:/Haviujaq(Haviujaq)/]"))
                .isProperName());
        addCase(AnalyzerCase("aagga", arrayOf("{aagga:aakka/1a}")));
        addCase(AnalyzerCase("aanniaqtulirijikkunnut", arrayOf("{aanniaq:aanniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("aanniaqtulirijikkut", arrayOf("{aanniaq:aanniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("aanniaqtulirinirmut", arrayOf("{aanniaq:aanniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("aanniaviliaqtunut", arrayOf("{aannia:aanniaq/1v}{vi:vik/3vn}{liaq:liaq/2nv}{tu:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("aaqqiumainnarutinut", arrayOf("{aaqqi:aaqqik/1v}{uma:ma/1vv}{inna:innaq/2vv}{ruti:ut/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("aglattiu", arrayOf("{aglat:allak/1v}{ti:ji/1vn}{u:up/tn-gen-s}")));
        addCase(AnalyzerCase("agluukkaq", null)
                .isProperName()
                .comment("Family name"));
        addCase(AnalyzerCase("aippaanik", arrayOf("{aippa:aippaq/1n}{anik:nganik/tn-acc-s-4s}")));
        addCase(AnalyzerCase("aippaanit", arrayOf("{aippa:aippaq/1n}{anit:nganit/tn-abl-s-4s}")));
        addCase(AnalyzerCase("aippanga", arrayOf("{aippa:aippaq/1n}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("aipuru", arrayOf("{aipuru:iipuru/1n}")));
        addCase(AnalyzerCase("ajauqtiit", arrayOf("{aja:ajak/1v}{uq:uq/3vv}{ti:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ajjaqsijii", arrayOf("{ajjaq:arjaq/1v}{si:si/1vv}{ji:ji/1vn}{i:k/tn-nom-d}")));
        addCase(AnalyzerCase("ajjaqsiji", arrayOf("{ajjaq:arjaq/1v}{si:si/1vv}{ji:ji/1vn}")));
        addCase(AnalyzerCase("ajjigiinngittunik", arrayOf("{ajji:ajji/1n}{gii:giik/2nv}{nngit:nngit/1vv}{tu:juq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("ajjigiinngittunit", arrayOf("{ajji:ajji/1n}{gii:giik/2nv}{nngit:nngit/1vv}{tu:juq/1vn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("ajjigiinngittunut", arrayOf("{ajji:ajji/1n}{gii:giik/2nv}{nngit:nngit/1vv}{tu:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ajjigiinngittut", arrayOf("{ajji:ajji/1n}{gii:giik/2nv}{nngit:nngit/1vv}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("akinga", arrayOf("{aki:aki/1n}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("akinginnut", arrayOf("{aki:aki/1n}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("akingit", arrayOf("{aki:aki/1n}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("akiqangittuq", arrayOf("{aki:aki/1n}{qa:qaq/1nv}{ngit:nngit/1vv}{tuq:juq/1vn}"))
                .isMisspelled());
        addCase(AnalyzerCase("akiraqtuqtut", arrayOf("{akiraq:akiraq/1v}{tuq:tuq/1vv}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("akisuk", null)
                .isProperName()
                .comment("Family name"));
        addCase(AnalyzerCase("akitujunut", arrayOf("{aki:aki/1n}{tu:tu/1nv}{ju:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("akitujutinut", arrayOf("{aki:aki/1n}{tu:tu/1nv}{ju:juq/1vn}{ti:ut/2nn}{nut:nut/tn-dat-p}"))
                .isMisspelled());
        addCase(AnalyzerCase("akitujuutinut", arrayOf("{aki:aki/1n}{tu:tu/1nv}{ju:juq/1vn}{uti:ut/2nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("aksualuk", arrayOf("{aksualuk:aksualuk/1a}")));
        addCase(AnalyzerCase("akulliq", arrayOf("{aku:aku/1n}{lliq:&iq/1nn}")));
        addCase(AnalyzerCase("akuni", arrayOf("{akuni:akuni/1n}")));
        addCase(AnalyzerCase("akunialuk", arrayOf("{akuni:akuni/1n}{aluk:aluk/1nn}")));
        addCase(AnalyzerCase("alakannuaq", null)
                .isProperName()
                .comment("Family name"));
        addCase(AnalyzerCase("alakkannuaq", null)
                .isProperName()
                .comment("Family name"));
        addCase(AnalyzerCase("alianaigusukpunga", arrayOf("{alianai:alianait/1v}{gusuk:gusuk/1vv}{punga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("allaat", arrayOf("{allaat:allaat/1a}")));
        addCase(AnalyzerCase("allatti", arrayOf("{allat:allak/1v}{ti:ji/1vn}")));
        addCase(AnalyzerCase("amisuni", arrayOf("{amisu:amisu/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("amisunik", arrayOf("{amisu:amisu/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("amisunit", arrayOf("{amisu:amisu/1n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("amisunut", arrayOf("{amisu:amisu/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("amisut", arrayOf("{amisut:amisut/1n}")));
        addCase(AnalyzerCase("amisuummata", arrayOf("{amisu:amisu/1n}{u:u/1nv}{mmata:mata/tv-caus-4p}")));
        addCase(AnalyzerCase("amisuuningit", arrayOf("{amisu:amisu/1n}{u:u/1nv}{ni:niq/2vn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("amittuq", arrayOf("{amit:amit/1v}{tuq:juq/1vn}")));
        addCase(AnalyzerCase("amittuq", null)
                .isProperName()
                .comment("community name"));
        addCase(AnalyzerCase("amma", arrayOf("{amma:amma/1c}")));
        addCase(AnalyzerCase("ammalu", arrayOf("{amma:amma/1c}{lu:lu/1q}")));
        addCase(AnalyzerCase("ammaluttauq", arrayOf("{amma:amma/1c}{lu:lu/1q}{ttauq:ttauq/1q}")));
        addCase(AnalyzerCase("ammattauq", arrayOf("{amma:amma/1c}{ttauq:ttauq/1q}")));
        addCase(AnalyzerCase("anaruaq", null)
                .isProperName()
                .comment("family name"));
        addCase(AnalyzerCase("angijumik", arrayOf("{angi:angi/1v}{ju:juq/1vn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("anginngittut", arrayOf("{angi:angi/1v}{nngit:nngit/1vv}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("anginngittut", arrayOf("{angi:angiq/1v}{nngit:nngit/1vv}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("angiqpisi", arrayOf("{angiq:angiq/1v}{pisi:visi/tv-int-2p}")));
        addCase(AnalyzerCase("angiqpisii", arrayOf("{angiq:angiq/1v}{pisii:visii/tv-int-2p}")));
        addCase(AnalyzerCase("angiqpugut", arrayOf("{angiq:angiq/1v}{pugut:vugut/tv-dec-1p}")));
        addCase(AnalyzerCase("angiqtugut", arrayOf("{angiq:angiq/1v}{tugut:jugut/tv-ger-1p}")));
        addCase(AnalyzerCase("angiqtut", arrayOf("{angiq:angiq/1v}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("anniaqarnangittulirinirmi", arrayOf("{annia:aanniaq/1n}{qar:qaq/1nv}{nangit:nanngit/1vv}{tu:juq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mi:mi/tn-loc-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("apiqqusiit", arrayOf("{apiq:apiq/1v}{qusi:usiq/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("apiqqusiksait", arrayOf("{apiq:apiq/1v}{qusi:usiq/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("apiqqusira", arrayOf("{apiq:apiq/1v}{qusi:usiq/1vn}{ra:ga/tn-nom-s-1s}")));
        addCase(AnalyzerCase("apiqqusirijara", arrayOf("{apiq:apiq/1v}{qusi:usiq/1vn}{ri:gi/1nv}{jara:jara/tv-ger-1s-3s}")));
        addCase(AnalyzerCase("apiqqusirnut", arrayOf("{apiq:apiq/1v}{qusir:usiq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("apiqqut", arrayOf("{apiq:apiq/1v}{qut:ut/1vn}")));
        addCase(AnalyzerCase("apiqquti", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}")));
        addCase(AnalyzerCase("apiqqutiga", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{ga:ga/tn-nom-s-1s}")));
        addCase(AnalyzerCase("apiqqutigali", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{ga:ga/tn-nom-s-1s}{li:li/1q}")));
        addCase(AnalyzerCase("apiqqutigijara", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{gi:gi/1nv}{jara:jara/tv-ger-1s-3s}")));
        addCase(AnalyzerCase("apiqqutiit", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("apiqqutik", arrayOf("{apiq:apiq/1v}{qutik:ut/1vn}")));
        addCase(AnalyzerCase("apiqqutiksait", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("apiqqutinga", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("apiqqutinganut", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{nganut:nganut/tn-dat-s-4s}")));
        addCase(AnalyzerCase("apiqqutinut", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("apiqqutissait", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{ssa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("apiqqutit", arrayOf("{apiq:apiq/1v}{qut:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("apiqqutitsait", arrayOf("{apiq:apiq/1v}{quti:ut/1vn}{tsa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("apirijumajara", arrayOf("{apiri:apiri/1v}{juma:juma/1vv}{jara:jara/tv-ger-1s-3s}")));
        addCase(AnalyzerCase("apirijumajunga", arrayOf("{apiri:apiri/1v}{juma:juma/1vv}{junga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("apirijumavara", arrayOf("{apiri:apiri/1v}{juma:juma/1vv}{vara:vara/tv-dec-1s-3s}")));
        addCase(AnalyzerCase("arnait", arrayOf("{arna:arnaq/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("arraagu", arrayOf("{arraagu:arraagu/1n}")));
        addCase(AnalyzerCase("arraaguit", arrayOf("{arraagu:arraagu/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("arraagumi", arrayOf("{arraagu:arraagu/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("arraagumut", arrayOf("{arraagu:arraagu/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("arraagunik", arrayOf("{arraagu:arraagu/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("arraagunit", arrayOf("{arraagu:arraagu/1n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("arraagunut", arrayOf("{arraagu:arraagu/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("arraagutamaat", arrayOf("{arraagu:arraagu/1n}{tamaa:tamaaq/1nn}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("arraaguttinni", arrayOf("{arraagu:arraagu/1n}{ttinni:ptingni/tn-loc-s-1d}")));
        addCase(AnalyzerCase("arraaguup", arrayOf("{arraagu:arraagu/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("arraani", arrayOf("{arraani:arraani/1n}")));
        addCase(AnalyzerCase("arragumi", arrayOf("{arragu:arraagu/1n}{mi:mi/tn-loc-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("arvaalluk", null)
                .isProperName()
                .comment("family name"));
        addCase(AnalyzerCase("arvaaluk", null)
                .isProperName()
                .comment("family name"));
        addCase(AnalyzerCase("arvaarluk", null)
                .isProperName()
                .comment("family name"));
        addCase(AnalyzerCase("arviani", arrayOf("{arvia:arviat/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("arviat", arrayOf("{arviat:arviat/1n}")));
        addCase(AnalyzerCase("asiagut", arrayOf("{asi:asi/1n}{agut:ngagut/tn-via-s-4s}")));
        addCase(AnalyzerCase("asiani", arrayOf("{asi:asi/1n}{ani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("asianik", arrayOf("{asi:asi/1n}{anik:nganik/tn-acc-s-4s}")));
        addCase(AnalyzerCase("asianut", arrayOf("{asi:asi/1n}{anut:nganut/tn-dat-s-4s}")));
        addCase(AnalyzerCase("asingi", arrayOf("{asi:asi/1n}{ngi:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("asingillu", arrayOf("{asi:asi/1n}{ngil:ngit/tn-nom-p-4s}{lu:lu/1q}")));
        addCase(AnalyzerCase("asinginni", arrayOf("{asi:asi/1n}{nginni:nginni/tn-loc-p-4s}")));
        addCase(AnalyzerCase("asinginnik", arrayOf("{asi:asi/1n}{nginnik:nginnik/tn-acc-p-4s}")));
        addCase(AnalyzerCase("asinginnillu", arrayOf("{asi:asi/1n}{nginnil:nginnit/tn-abl-p-4s}{lu:lu/1q}")));
        addCase(AnalyzerCase("asinginnit", arrayOf("{asi:asi/1n}{nginnit:nginnit/tn-abl-p-4s}")));
        addCase(AnalyzerCase("asinginnullu", arrayOf("{asi:asi/1n}{nginnul:nginnut/tn-dat-p-4s}{lu:lu/1q}")));
        addCase(AnalyzerCase("asingit", arrayOf("{asi:asi/1n}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("asuillaak", arrayOf("{asuillaak:asuilaak/1a}"))
                .isMisspelled());
        addCase(AnalyzerCase("ataani", arrayOf("{ata:ata/1n}{ani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("atausiq", arrayOf("{atausiq:atausiq/1n}")));
        addCase(AnalyzerCase("atausirmi", arrayOf("{atausir:atausiq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("atausirmik", arrayOf("{atausir:atausiq/1n}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("atausirmut", arrayOf("{atausir:atausiq/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("atii", arrayOf("{atii:atii/1e}")));
        addCase(AnalyzerCase("atiliurutausimajut", arrayOf("{ati:atiq/1n}{liu:liuq/1nv}{ruta:ut/1vn}{u:u/1nv}{sima:sima/1vv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("atinga", arrayOf("{ati:atiq/1n}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("atingit", arrayOf("{ati:atiq/1n}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("atirnik", arrayOf("{atir:atiq/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("atsualuk", arrayOf("{atsualuk:aksualuk/1a}")));
        addCase(AnalyzerCase("atuagait", arrayOf("{atu:atuq/1v}{a:a/1vv}{ga:gaq/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("atuagaq", arrayOf("{atu:atuq/1v}{a:a/1vv}{gaq:gaq/1vn}")));
        addCase(AnalyzerCase("atuagarmik", arrayOf("{atu:atuq/1v}{a:a/1vv}{gar:gaq/1vn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("atuagarnik", arrayOf("{atu:atuq/1v}{a:a/1vv}{gar:gaq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("atuliqujaujuq", arrayOf("{atu:atuq/1v}{li:li/2vv}{qu:qu/2vv}{ja:jaq/1vn}{u:u/1nv}{juq:juq/1vn}",
            "{atu:atuq/2v}{li:li/2vv}{qu:qu/2vv}{ja:jaq/1vn}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("atuni", arrayOf("{atuni:atunit/1n}")));
        addCase(AnalyzerCase("atunit", arrayOf("{atunit:atunit/1n}")));
        addCase(AnalyzerCase("atuqtuksait", arrayOf("{atuq:atuq/1v}{tuksa:juksaq/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("atuqtuksanut", arrayOf("{atuq:atuq/1v}{tuksa:juksaq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("aujakkut", arrayOf("{auja:aujaq/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("aulajjutinut", arrayOf("{aula:aula/1v}{jjuti:ut/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("aulaninginnut", arrayOf("{aula:aula/1v}{ni:niq/2vn}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("aulanirmut", arrayOf("{aula:aula/1v}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("aulattijiit", arrayOf("{aulat:aulat/1v}{ti:si/1vv}{ji:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("aulattinirmut", arrayOf("{aulat:aulat/1v}{ti:si/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("aviktursimajuni", arrayOf("{avik:avik/1v}{tur:tuq/1vv}{sima:sima/1vv}{ju:juq/1vn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("avittuqsimajuni", arrayOf("{avit:avik/1v}{tuq:tuq/1vv}{sima:sima/1vv}{ju:juq/1vn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("avittuqsimajunut", arrayOf("{avit:avik/1v}{tuq:tuq/1vv}{sima:sima/1vv}{ju:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("avittuqsimajut", arrayOf("{avit:avik/1v}{tuq:tuq/1vv}{sima:sima/1vv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("avittuqsimajutigut", arrayOf("{avit:avik/1v}{tuq:tuq/1vv}{sima:sima/1vv}{ju:juq/1vn}{tigut:tigut/tn-via-p}")));
        addCase(AnalyzerCase("gaasalii", arrayOf("{gaasalii:gaasalii/1n}")));
        addCase(AnalyzerCase("gavama", arrayOf("{gavama:gavama/1n}")));
        addCase(AnalyzerCase("gavamakkunginni", arrayOf("{gavama:gavama/1n}{kku:kkut/1nn}{nginni:nginni/tn-loc-p-4s}")));
        addCase(AnalyzerCase("gavamakkunginnit", arrayOf("{gavama:gavama/1n}{kku:kkut/1nn}{nginnit:nginnit/tn-abl-p-4s}")));
        addCase(AnalyzerCase("gavamakkunginnut", arrayOf("{gavama:gavama/1n}{kku:kkut/1nn}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("gavamakkungit", arrayOf("{gavama:gavama/1n}{kku:kkut/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("gavamakkungita", arrayOf("{gavama:gavama/1n}{kku:kkut/1nn}{ngita:ngita/tn-gen-p-4s}")));
        addCase(AnalyzerCase("gavamakkunni", arrayOf("{gavama:gavama/1n}{kkun:kkut/1nn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("gavamakkunnik", arrayOf("{gavama:gavama/1n}{kkun:kkut/1nn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("gavamakkunnut", arrayOf("{gavama:gavama/1n}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("gavamakkut", arrayOf("{gavama:gavama/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("gavamalirijikkut", arrayOf("{gavama:gavama/1n}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("gavamalirinirmut", arrayOf("{gavama:gavama/1n}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("gavamanga", arrayOf("{gavama:gavama/1n}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("gavamanganut", arrayOf("{gavama:gavama/1n}{nganut:nganut/tn-dat-s-4s}")));
        addCase(AnalyzerCase("gavamangata", arrayOf("{gavama:gavama/1n}{ngata:ngata/tn-gen-s-4s}")));
        addCase(AnalyzerCase("gavamanginnut", arrayOf("{gavama:gavama/1n}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("gavamangit", arrayOf("{gavama:gavama/1n}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("gavamanut", arrayOf("{gavama:gavama/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("gavamatuqakkunni", arrayOf("{gavama:gavama/1n}{tuqa:tuqaq/1nn}{kkun:kkut/1nn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("gavamatuqakkunnik", arrayOf("{gavama:gavama/1n}{tuqa:tuqaq/1nn}{kkun:kkut/1nn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("gavamatuqakkunnit", arrayOf("{gavama:gavama/1n}{tuqa:tuqaq/1nn}{kkun:kkut/1nn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("gavamatuqakkunnut", arrayOf("{gavama:gavama/1n}{tuqa:tuqaq/1nn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("gavamatuqakkut", arrayOf("{gavama:gavama/1n}{tuqa:tuqaq/1nn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("gavamaulluta", arrayOf("{gavama:gavama/1n}{u:u/1nv}{lluta:luta/tv-part-1p-prespas}")));
        addCase(AnalyzerCase("gavamaup", arrayOf("{gavama:gavama/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("gavamavut", arrayOf("{gavama:gavama/1n}{vut:vut/tn-nom-s-1p}")));
        addCase(AnalyzerCase("gilin", null)
                .isProperName()
                .comment("Glen"));
        addCase(AnalyzerCase("guriin", null)
                .isProperName()
                .comment("Green"));
        addCase(AnalyzerCase("iat", null)
                .isProperName()
                .comment("Ed"));
        addCase(AnalyzerCase("igluit", arrayOf("{iglu:iglu/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("iglulirijikkut", arrayOf("{iglu:iglu/1n}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("iglulirijirjuakkut", arrayOf("{iglu:iglu/1n}{liri:liri/1nv}{ji:ji/1vn}{rjua:juaq/1nn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("iglulirinirmut", arrayOf("{iglu:iglu/1n}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("ii", arrayOf("{ii:ii/1a}")));
        addCase(AnalyzerCase("iing", null)
                .isProperName()
                .comment("Ng"));
        addCase(AnalyzerCase("iipuru", arrayOf("{iipuru:iipuru/1n}")));
        addCase(AnalyzerCase("iipurul", arrayOf("{iipurul:iipuru/1n}")));
        addCase(AnalyzerCase("iituaq", null)
                .isProperName()
                .comment("Edward"));
        addCase(AnalyzerCase("ikajuqtiit", arrayOf("{ikajuq:ikajuq/1v}{ti:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ikajuutit", arrayOf("{ikaju:ikajuq/1v}{ut:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ikkarialuk", arrayOf("[decomposition:/ikkarialuk(ikkarialuk)/]"))
                .isProperName());
        addCase(AnalyzerCase("ikkarrialuk", arrayOf("[decomposition:/ikkarrialuk(ikkarrialuk)/]"))
                .isProperName());
        addCase(AnalyzerCase("ikkarrialuuk", arrayOf("[decomposition:/ikkarrialuuk(ikkarrialuuk)/]"))
                .isProperName());
        addCase(AnalyzerCase("ikpaksaq", arrayOf("{ikpaksaq:ikpaksaq/1a}")));
        addCase(AnalyzerCase("iksivauitaaq", arrayOf("{iksiva:iksiva/1v}{uitaaq:ut/1vn}"))
                .isMisspelled());
        addCase(AnalyzerCase("iksivauta", arrayOf("{iksiva:iksiva/1v}{uta:ut/1vn}")));
        addCase(AnalyzerCase("iksivautaa", arrayOf("{iksiva:iksiva/1v}{uta:ut/1vn}{a:k/tn-nom-d}")));

//		2020-04, BF
//		iksivautaaq :	fréquence : 15140 – et ses variantes « itsivautaaq » (2571), « issivautaaq » (1941)
//		Ce mot (fréquence : 15140) est toujours utilisé en adresse à cette personne qui est le « Mr. Speaker ». Dans le même contexte, on trouve également les formes
//		« iksivautaq » (29893), « itsivautaq » (4381), « issivautaq » (2733)
//		« iksivautaa » (2493), « itsivautaa » (787), « issivautaa » (104)
//
//		La forme en « utaq » est de loin beaucoup plus fréquente que les formes en « utaaq » et « utaa » et s’analyse avec, comme dernier morphème, le morphème « ut » dont une des formes est « utaq ».
//
//		Les formes en « utaaq » et « utaa » sont en fait la même forme « utaaq » où le « q » dans la 2ème forme a été escamotée, phénomène rencontré très souvent : la consonne finale d’un mot n’est pas écrite. Mais comment s’explique cette forme « utaaq » avec un double « a » ? Je crois que le double « a » vient du contexte d’adresse : on utilise dans ce contexte une forme « vocative » qui, normalement, se réalise par la forme duelle : suppression de la consonne finale, allongement (dédoublement) de la voyelle et ajout de « k ». Dans ces cas-ci, il n’y a pas de « k ». Est-ce une erreur ? Je ne le sais pas.
//
//		* pour l’instant, taggons le comme possiblyMisspelled()
//
        addCase(AnalyzerCase("iksivautaaq", null)
                .possiblyMisspelledWord());
        addCase(AnalyzerCase("iksivautap", arrayOf("{iksiva:iksiva/1v}{uta:ut/1vn}{p:up/tn-gen-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("iksivautaq", arrayOf("{iksiva:iksiva/1v}{utaq:ut/1vn}")));
        addCase(AnalyzerCase("iksivautaup", arrayOf("{iksiva:iksiva/1v}{uta:ut/1vn}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("ikummaqqutilirijikkunnut", arrayOf("{ikummaq:ikummaq/1v}{quti:ut/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}"))
                .comment("NT Power Corporation"));
        addCase(AnalyzerCase("ikupigvilirijikkunnut", arrayOf("{ikupig:ikupik/1v}{vi:vik/3vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ikupigvilirijikkut", arrayOf("{ikupig:ikupik/1v}{vi:vik/3vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("ikupivvilirijikkunnut", arrayOf("{ikupiv:ikupik/1v}{vi:vik/3vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ikupivvilirijikkut", arrayOf("{ikupiv:ikupik/1v}{vi:vik/3vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("ilaak", arrayOf("{ilaak:ilaak/1e}")));
        addCase(AnalyzerCase("ilaannikkut", arrayOf("{ilaanni:ilaanni/1a}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("ilaaqai", arrayOf("{ilaa:ilaak/1e}{qai:qai/1q}")));
        addCase(AnalyzerCase("ilagiarut", arrayOf("{ila:ila/1v}{gia:giaq/1vv}{rut:ut/1vn}")));
        addCase(AnalyzerCase("ilagiaruti", arrayOf("{ila:ila/1v}{gia:giaq/1vv}{ruti:ut/1vn}")));
        addCase(AnalyzerCase("ilagiarutiit", arrayOf("{ila:ila/1v}{gia:giaq/1vv}{ruti:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ilagiarutit", arrayOf("{ila:ila/1v}{gia:giaq/1vv}{rut:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ilagijaujuq", arrayOf("{ila:ila/1n}{gi:gi/1nv}{ja:jaq/1vn}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("ilagijaujut", arrayOf("{ila:ila/1n}{gi:gi/1nv}{ja:jaq/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("ilanga", arrayOf("{ila:ila/1n}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("ilangagut", arrayOf("{ila:ila/1n}{ngagut:ngagut/tn-via-s-4s}")));
        addCase(AnalyzerCase("ilangani", arrayOf("{ila:ila/1n}{ngani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("ilangat", arrayOf("{ila:ila/1n}{ngat:ngat/tn-nom-s-4p}")));
        addCase(AnalyzerCase("ilangi", arrayOf("{ila:ila/1n}{ngi:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("ilangillu", arrayOf("{ila:ila/1n}{ngil:ngit/tn-nom-p-4s}{lu:lu/1q}")));
        addCase(AnalyzerCase("ilanginni", arrayOf("{ila:ila/1n}{nginni:nginni/tn-loc-p-4s}")));
        addCase(AnalyzerCase("ilanginnik", arrayOf("{ila:ila/1n}{nginnik:nginnik/tn-acc-p-4s}")));
        addCase(AnalyzerCase("ilanginnit", arrayOf("{ila:ila/1n}{nginnit:nginnit/tn-abl-p-4s}")));
        addCase(AnalyzerCase("ilanginnut", arrayOf("{ila:ila/1n}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("ilangit", arrayOf("{ila:ila/1n}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("ilinnianirmut", arrayOf("{ilinnia:ilinniaq/1v}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("ilinniaqtiit", arrayOf("{ilinniaq:ilinniaq/1v}{ti:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ilinniaqtinik", arrayOf("{ilinniaq:ilinniaq/1v}{ti:ji/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("ilinniaqtinut", arrayOf("{ilinniaq:ilinniaq/1v}{ti:ji/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ilinniaqtiujut", arrayOf("{ilinniaq:ilinniaq/1v}{ti:ji/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("ilinniaqtulirijikkunnut", arrayOf("{ilinniaq:ilinniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ilinniaqtulirijikkut", arrayOf("{ilinniaq:ilinniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("ilinniaqtulirinirmut", arrayOf("{ilinniaq:ilinniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("ilinniaqtunut", arrayOf("{ilinniaq:ilinniaq/1v}{tu:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ilinniaqtut", arrayOf("{ilinniaq:ilinniaq/1v}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("ilinniarniq", arrayOf("{ilinniar:ilinniaq/1v}{niq:niq/2vn}")));
        addCase(AnalyzerCase("ilinniarnirmut", arrayOf("{ilinniar:ilinniaq/1v}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("ilinniartulirijiit", arrayOf("{ilinniar:ilinniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ilinniarviit", arrayOf("{ilinniar:ilinniaq/1v}{vi:vik/3vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ilinniarvik", arrayOf("{ilinniar:ilinniaq/1v}{vik:vik/3vn}")));
        addCase(AnalyzerCase("ilinniarvimmi", arrayOf("{ilinniar:ilinniaq/1v}{vim:vik/3vn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("ilinniarviup", arrayOf("{ilinniar:ilinniaq/1v}{vi:vik/3vn}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("iliqqusilirijikkunnut", arrayOf("{iliqqusi:iliqqusiq/1n}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("iliqqusilirijikkut", arrayOf("{iliqqusi:iliqqusiq/1n}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("iliqqusilirinirmut", arrayOf("{iliqqusi:iliqqusiq/1n}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("ilisaijiit", arrayOf("{ilisaiji:ilisaiji/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ilisaijinut", arrayOf("{ilisaiji:ilisaiji/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ilitaqsiniq", arrayOf("{ilitaq:ilitaq/1v}{si:si/3vv}{niq:niq/2vn}")));
        addCase(AnalyzerCase("ilitarijauningit", arrayOf("{ilita:ilitaq/1v}{ri:gi/4vv}{ja:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{ngit:ngit/tn-nom-p-4p}")));
        addCase(AnalyzerCase("illaqtut", arrayOf("{illaq:iglaq/1v}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("illu", arrayOf("{illu:iglu/1n}")));
        addCase(AnalyzerCase("illuit", arrayOf("{illu:iglu/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("illulirijikkut", arrayOf("{illu:iglu/1n}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("illulirijirjuakkut", arrayOf("{illu:iglu/1n}{liri:liri/1nv}{ji:ji/1vn}{rjua:juaq/1nn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("illulirinirmut", arrayOf("{illu:iglu/1n}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("illumi", arrayOf("{illu:iglu/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("illumik", arrayOf("{illu:iglu/1n}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("illumut", arrayOf("{illu:iglu/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("illuni", arrayOf("{illu:iglu/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("illunik", arrayOf("{illu:iglu/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("illunut", arrayOf("{illu:iglu/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("iluagut", arrayOf("{ilu:ilu/1n}{agut:ngagut/tn-via-s-4s}")));
        addCase(AnalyzerCase("iluani", arrayOf("{ilu:ilu/1n}{ani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("ilulingit", arrayOf("{ilu:ilu/1n}{li:lik/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("ilulingita", arrayOf("{ilu:ilu/1n}{li:lik/1nn}{ngita:ngita/tn-gen-p-4s}")));
        addCase(AnalyzerCase("iluunnatik", arrayOf("{iluunnatik:iluunnatik/1p}")));
        addCase(AnalyzerCase("imaak", arrayOf("{imaak:imaak/1a}")));

//		2020-04, BF:
//		imaimmat :	Fréquence = 229 "whereas", "as it is"
//				C'est compliqué, comme tout ce qui touche aux adverbes.
//				Il y a un mot dans Spalding : "imannaittuq" qui signifie "one like this (as shown)". Ce mot est basé sur la racine "imanna", avec ce que je crois être le suffixe "it", et finalement suffixe "juq/tuq". On y associe le mot "imaittuq" utilisé dans South Baffin et East Coast Hudson Bay, et le mot "tamainnaittuq" (même sens). "ima" serait une forme de "imanna". Il y a aussi une entrée "taimaittuq" qui signifie "it is like that" ou "one like that (which is shown)" associé à "imaittuq".
//				"ta" est un « préfixe », unique en son genre en inuktitut, qui se place devant les adverbes. Il a une signification particulière dont je ne me souviens plus des détails. Il y a toujours des duos "X" et "taX", comme "ima" et "taima", "imanna" et "taimanna".
//				Le mot "imaimmat" est d'après moi la même chose :  ima + it + mat où "mat" est une terminaison verbale [n'oublie pas que "juq/tuq" est à la fois suffixe ET terminaison verbale].
//				Je pense qu'on devrait définir la décomposition de ce mot comme ceci :
//				{ima:ima/1a}{im:it/3nv}{mat:mat/tv-caus-4s}
//				de la même façon que "taimaimmat", qui est effectivement analysé avec succès par l'analyseur :
//				{taima:taima/1a}{im:it/3nv}{mat:mat/tv-caus-4s}
//				Pour l'instant, "imaimmat" ne sera pas décomposé parce que la forme "ima" n'est pas connue. "taima" est un adverbe dans la base de données qui signifie 1) "so", "therefore" 2) "That's it! It's finished!", "Enough! Stop!". Je suppose qu'on pourrait ajouter la forme "ima" en parallèle à "taima", mais comme j'ai dit, les adverbes, c'est compliqué, j'avais l'intention de revisiter ça après avoir réétudié ça, mais jamais eu le temps.
        addCase(AnalyzerCase("imaimmat", arrayOf("{ima:ima/1a}{im:it/3nv}{mat:mat/tv-caus-4s}")));

        addCase(AnalyzerCase("imanna", arrayOf("{imanna:imannak/1a}")));
        addCase(AnalyzerCase("imannak", arrayOf("{imannak:imannak/1a}")));
        addCase(AnalyzerCase("immagaa", arrayOf("{immagaa:immaqaa/1a}"))
                .isMisspelled());
        addCase(AnalyzerCase("immaqa", arrayOf("{immaqa:immaqaa/1a}"))
                .isMisspelled());
        addCase(AnalyzerCase("imminik", arrayOf("{imminik:imminik/1a}")));
        addCase(AnalyzerCase("ing", null)
                .isProperName()
                .comment("Ng"));
        addCase(AnalyzerCase("ingirrajulirijikkunnut", arrayOf("{ingirra:ingirra/1v}{ju:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ingirrajulirijikkut", arrayOf("{ingirra:ingirra/1v}{ju:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("ingirrajulirinirmut", arrayOf("{ingirra:ingirra/1v}{ju:juq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("innatuqait", arrayOf("{inna:innaq/1n}{tuqa:tuqaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("innatuqarnik", arrayOf("{inna:innaq/1n}{tuqar:tuqaq/1nn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("innatuqarnut", arrayOf("{inna:innaq/1n}{tuqar:tuqaq/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("innirvik", arrayOf("[decomposition:/innirvik(innirvik)/]"))
                .isProperName());
// 2020-04, BF:
//		inuinnaqtun :	Ceci est le nom du dialecte parlé à l’ouest du Nunavut.
//		Il se décompose en inu + innaq + tun, où « tun » correspond à « tut »
//		utilisé ailleurs ; il est usuel de trouver « n » à la fin des mots au
//		lieu de « t ».
//		Ce mot est à la base de plusieurs mots
//		Je ne sais pas trop pour l’instant quoi faire avec ce mot :
//		- l’ajouter tel quel dans la base de données, mais sous quelle
//		  dénomination ? ce n’est pas un nom, ce n’est pas un verbe. Peut-être
//		  dans le fichier CommonCompositeWords.csv avec « inuinnaqtut » dans le
//		  champ « morpheme » et « inuinnaqtun » dans le champ « variant » ;
//		  à expérimenter.
//		Pour le moment, on le taggue comme decomposition unknown
//
        addCase(AnalyzerCase("inuinnaqtun", arrayOf("[decomposition:/inuinnaqtun(inuinnaqtun)/]"))
                .correctDecompUnknown());
        addCase(AnalyzerCase("inuit", arrayOf("{inu:inuk/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("inuk", arrayOf("{inuk:inuk/1n}")));
        addCase(AnalyzerCase("inuki", arrayOf("[decomposition:/inuki(inuki)/]"))
                .isProperName());
        addCase(AnalyzerCase("inuktitut", arrayOf("{inuk:inuk/1n}{titut:titut/tn-sim-p}")));
        addCase(AnalyzerCase("inulimaanut", arrayOf("{inu:inuk/1n}{limaa:limaaq/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("inulirijikkullu", arrayOf("{inu:inuk/1n}{liri:liri/1nv}{ji:ji/1vn}{kkul:kkut/1nn}{lu:lu/1q}")));
        addCase(AnalyzerCase("inulirijikkunnut", arrayOf("{inu:inuk/1n}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("inulirijikkut", arrayOf("{inu:inuk/1n}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("inulirinirmut", arrayOf("{inu:inuk/1n}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("inunginnut", arrayOf("{inu:inuk/1n}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("inungit", arrayOf("{inu:inuk/1n}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("inungni", arrayOf("{inung:inuk/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("inungnut", arrayOf("{inung:inuk/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("inunni", arrayOf("{inun:inuk/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("inunnik", arrayOf("{inun:inuk/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("inunnit", arrayOf("{inun:inuk/1n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("inunnu", arrayOf("{inun:inuk/1n}{nu:nut/tn-dat-p}")));
        addCase(AnalyzerCase("inunnut", arrayOf("{inun:inuk/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("inuqutivut", arrayOf("{inu:inuk/1n}{quti:quti/1nn}{vut:vut/tn-nom-s-1p}")));
        addCase(AnalyzerCase("inuttitut", arrayOf("{inut:inuk/1n}{titut:titut/tn-sim-p}")));
        addCase(AnalyzerCase("inutuqait", arrayOf("{inu:inuk/1n}{tuqa:tuqaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("inuusilirijikkunullu", arrayOf("{inu:inuk/1n}{u:u/1nv}{si:siq/2vn}{liri:liri/1nv}{ji:ji/1vn}{kku:kkut/1nn}{nul:nut/tn-dat-p}{lu:lu/1q}")));
        addCase(AnalyzerCase("ippaksaq", arrayOf("{ippaksaq:ikpaksaq/1a}")));
        addCase(AnalyzerCase("ippassaq", arrayOf("{ippassaq:ikpaksaq/1a}")));
        addCase(AnalyzerCase("ippatsaq", arrayOf("{ippatsaq:ikpaksaq/1a}")));
        addCase(AnalyzerCase("iqaluit", arrayOf("{iqalu:iqaluk/2n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("iqaluit", arrayOf("{iqaluit:iqaluit/1n}")));
        addCase(AnalyzerCase("iqalungni", arrayOf("{iqalung:iqaluk/2n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("iqalunni", arrayOf("{iqalun:iqaluk/2n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("iqalunnit", arrayOf("{iqalun:iqaluk/2n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("iqalunnut", arrayOf("{iqalun:iqaluk/2n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("iqaluttuuttiaq", arrayOf("{iqaluttuuttiaq:iqaluktuutsiaq/1n}")));
        addCase(AnalyzerCase("iqittuq", arrayOf("[decomposition:/iqittuq(iqittuq)/]"))
                .isProperName());
        addCase(AnalyzerCase("iqqanaijaat", arrayOf("{iqqanaijaa:iqqanaijaaq/1n}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("iqqanaijaqtiit", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{ti:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("iqqanaijaqtikka", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{ti:ji/1vn}{kka:kka/tn-nom-p-1s}")));
        addCase(AnalyzerCase("iqqanaijaqtinginnut", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{ti:ji/1vn}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("iqqanaijaqtingit", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{ti:ji/1vn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("iqqanaijaqtinik", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{ti:ji/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("iqqanaijaqtinit", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{ti:ji/1vn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("iqqanaijaqtinut", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{ti:ji/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("iqqanaijaqtiujut", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{ti:ji/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("iqqanaijaqtulirijikkunnit", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("iqqanaijaqtulirijikkut", arrayOf("{iqqanaijaq:iqqanaijaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("iqqaqtuivilirijikkunnut", arrayOf("{iqqaq:iqqaq/1v}{tu:tuq/1vv}{i:i/1vv}{vi:vik/3vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("iqqaqtuivilirijikkut", arrayOf("{iqqaq:iqqaq/1v}{tu:tuq/1vv}{i:i/1vv}{vi:vik/3vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("iqqittuq", arrayOf("[decomposition:/iqqittuq(iqqittuq)/]"))
                .isProperName());
        addCase(AnalyzerCase("issivauta", arrayOf("{issiva:iksiva/1v}{uta:ut/1vn}")));
        addCase(AnalyzerCase("issivautaa", arrayOf("{issiva:iksiva/1v}{uta:ut/1vn}{a:k/tn-nom-d}")));
        addCase(AnalyzerCase("issivautaaq", arrayOf(""))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("issivautaq", arrayOf("{issiva:iksiva/1v}{utaq:ut/1vn}")));
        addCase(AnalyzerCase("isumagillugu", arrayOf("{isuma:isuma/1n}{gi:gi/1nv}{llugu:lugu/tv-part-1s-3s-prespas}")));
        addCase(AnalyzerCase("itsivauta", arrayOf("{itsiva:iksiva/1v}{uta:ut/1vn}")));
        addCase(AnalyzerCase("itsivautaa", arrayOf("{itsiva:iksiva/1v}{uta:ut/1vn}{a:k/tn-nom-d}")));
        addCase(AnalyzerCase("itsivautaaq", null)
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; [decomposition:/itsiva(iksiva)/utaaq(ut)/]"));
        addCase(AnalyzerCase("itsivautaq", arrayOf("{itsiva:iksiva/1v}{utaq:ut/1vn}")));
        addCase(AnalyzerCase("ivvit", arrayOf("{ivvit:igvit/1p}")));
        addCase(AnalyzerCase("jaak", arrayOf("[decomposition:/jaak(jaak)/]"))
                .isProperName());
        addCase(AnalyzerCase("jaan", arrayOf("[decomposition:/jaan(jaan)/]"))
                .isProperName());
        addCase(AnalyzerCase("jaimisi", arrayOf("[decomposition:/jaimisi(jaimisi)/]"))
                .isProperName());
        addCase(AnalyzerCase("jaims", arrayOf("[decomposition:/jaims(jaims)/]"))
                .isProperName());
        addCase(AnalyzerCase("julai", arrayOf("{julai:julai/1n}")));
        addCase(AnalyzerCase("juraias", arrayOf("[decomposition:/juraias(juraias)/]"))
                .isProperName());
        addCase(AnalyzerCase("juupi", arrayOf("[decomposition:/juupi(juupi)/]"))
                .isProperName());
        addCase(AnalyzerCase("kaalvin", arrayOf("[decomposition:/kaalvin(kaalvin)/]"))
                .isProperName());
        addCase(AnalyzerCase("kajusijuq", arrayOf("{kajusi:kajusi/1v}{juq:juq/1vn}")));
        addCase(AnalyzerCase("kajusivuq", arrayOf("{kajusi:kajusi/1v}{vuq:vuq/tv-dec-3s}")));
        addCase(AnalyzerCase("kamagijalik", arrayOf("{kama:kama/1v}{gi:gi/4vv}{ja:jaq/1vn}{lik:lik/1nn}")));
        addCase(AnalyzerCase("kamajuq", arrayOf("{kama:kama/1v}{juq:juq/1vn}")));
        addCase(AnalyzerCase("kamisana", arrayOf("{kamisana:kamisina/1n}")));
        addCase(AnalyzerCase("kamisina", arrayOf("{kamisina:kamisina/1n}")));
        addCase(AnalyzerCase("kamisinaup", arrayOf("{kamisina:kamisina/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("kanannangani", arrayOf("{kananna:kanangnaq/1n}{ngani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("kanata", arrayOf("{kanata:kanata/1n}")));
        addCase(AnalyzerCase("kanatalimaami", arrayOf("{kanata:kanata/1n}{limaa:limaaq/1nn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("kanatami", arrayOf("{kanata:kanata/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("kanataup", arrayOf("{kanata:kanata/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("kangiqtugaapimmi", arrayOf("{kangiqtugaapim:kangiqtugaapik/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("katilimaaqtugit", arrayOf("{kati:kati/1v}{limaaq:limaaq/2vv}{tugit:lugit/tv-part-1s-3p-prespas}")));
        addCase(AnalyzerCase("katillugit", arrayOf("{kati:kati/1v}{llugit:lugit/tv-part-1s-3p-prespas}")));
        addCase(AnalyzerCase("katimaji", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}")));
        addCase(AnalyzerCase("katimajiit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("katimajilimaat", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{limaa:limaaq/1nn}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("katimajingannit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ngannit:ngangnit/tn-abl-s-4d}")));
        addCase(AnalyzerCase("katimajinginnik", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{nginnik:nginnik/tn-acc-p-4s}")));
        addCase(AnalyzerCase("katimajinginnit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{nginnit:nginnit/tn-abl-p-4s}")));
        addCase(AnalyzerCase("katimajinginnut", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("katimajingit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("katimajingita", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ngita:ngita/tn-gen-p-4s}")));
        addCase(AnalyzerCase("katimajinut", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("katimajiralaa", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}")));
        addCase(AnalyzerCase("katimajiralaangit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("katimajiralaanguinnaqtut", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{ngu:u/1nv}{innaq:innaq/2vv}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("katimajiralaangukainnaqtunut", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{ngu:u/1nv}{kainnaq:kainnaq/1vv}{tu:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("katimajiralaani", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("katimajiralaanit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("katimajiralaanut", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("katimajiralaat", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{t:it/tn-nom-p}")));

//		2020-04, BF:
//		katimajit :	(fréquence : 189) le mot devrait être « katimajiit » (fréquence : 2767).
//		Il s’agit ici du phénomène par lequel le pluriel, qui devrait normalement être « it », est rendu par « t », sans le « i ».  Je ne sais pas quoi faire avec ce phénomène, c’est pourquoi j’ai mis le mot avec @.
//
//		J’ai trouvé dernièrement de l’information sur le pluriel sans « i » qui expliquerait son absence dans les mots finissant par le morphème « ksaq ». Mais j’ai besoin d’étudier ça davantage.
//
//		Même chose pour : maligaliuqtit, pigiaqtitat, piliriaksat, piqujaksat, uqausiksat
//
//		Pour le moment, tagggons le comme possiblyMisspelled()
//
        addCase(AnalyzerCase("katimajit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{t:it/tn-nom-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment"));
        addCase(AnalyzerCase("katimajiujut", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("katimajiuqataujut", arrayOf("{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{u:u/1nv}{qatau:qatau/1vv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("katimajjutiksait", arrayOf("{kati:kati/1v}{ma:ma/1vv}{jjuti:jjut/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("katimajjutiksanut", arrayOf("{kati:kati/1v}{ma:ma/1vv}{jjuti:jjut/1vn}{ksa:ksaq/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("katimajjutiksaq", arrayOf("{kati:kati/1v}{ma:ma/1vv}{jjuti:jjut/1vn}{ksaq:ksaq/1nn}")));
        addCase(AnalyzerCase("katimajjutissaq", arrayOf("{kati:kati/1v}{ma:ma/1vv}{jjuti:jjut/1vn}{ssaq:ksaq/1nn}")));
        addCase(AnalyzerCase("katimaniq", arrayOf("{kati:kati/1v}{ma:ma/1vv}{niq:niq/2vn}")));
        addCase(AnalyzerCase("katimatillugit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{tillugit:tillugit/tv-part-3p}")));
        addCase(AnalyzerCase("katimatuinnaqtillugit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{tuinnaq:tuinnaq/1vv}{tillugit:tillugit/tv-part-3p}")));
        addCase(AnalyzerCase("katimautiminingit", arrayOf("{kati:kati/1v}{ma:ma/1vv}{uti:ut/1vn}{mini:miniq/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("katimautinik", arrayOf("{kati:kati/1v}{ma:ma/1vv}{uti:ut/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("katimautitsa", arrayOf("{kati:kati/1v}{ma:ma/1vv}{uti:ut/1vn}{tsa:ksaq/1nn}")));
        addCase(AnalyzerCase("katimavimmi", arrayOf("{kati:kati/1v}{ma:ma/1vv}{vim:vik/3vn}{mi:mi/tn-loc-s}")));

//		2020-04, BF:
//		katimmajjutiksaq :	(fréquence : 514) le mot est basé sur la racine
//		« katima ». On trouve la variante « katimajjutiksaq » avec une
//		fréquence de 2782. La seule façon dont je peux expliquer le 2ème « m »
//		est le phénomène de l’« inchoativité » qui consiste à doubler la
//		dernière consonne d’une racine verbale. « Inchoatif » signifie en gros
//		« commencer à être, à arriver ». J’ai cru comprendre que ce n’est pas
//		quelque chose qui s’applique à toutes les racines verbales, alors en
//		attendant d’en savoir plus sur le sujet, j’ai décidé de le laisser de
//		côté dans l’analyse morphologique, de là le correctDecompUnknown().
//
        addCase(AnalyzerCase("katimmajjutiksaq", arrayOf("{kati:kati/1v}{mma:ma/1v}{jjuti:jjut/1vn}{ksaq:ksaq/1nn}"))
                .correctDecompUnknown());
        addCase(AnalyzerCase("katitsutik", arrayOf("{katit:katit/1v}{sutik:lutik/tv-part-3p-prespas}")));
        addCase(AnalyzerCase("katittugit", arrayOf("{katit:katit/1v}{tugit:lugit/tv-part-4p-3p-prespas}")));
        addCase(AnalyzerCase("kattuk", arrayOf("[decomposition:/kattuk(kattuk)/]"))
                .isProperName());
        addCase(AnalyzerCase("kattuq", arrayOf("[decomposition:/kattuq(kattuq)/]"))
                .isProperName());
        addCase(AnalyzerCase("katujjiqatigiingit", arrayOf("{katujji:katujji/1v}{qati:qati/1vn}{gii:giik/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("katujjiqatigiit", arrayOf("{katujji:katujji/1v}{qati:qati/1vn}{gii:giik/1nn}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("kialvin", arrayOf("[decomposition:/kialvin(kialvin)/]"))
                .isProperName());
        addCase(AnalyzerCase("kiinaujait", arrayOf("{kiinauja:kiinaujaq/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("kiinaujalirijiit", arrayOf("{kiinauja:kiinaujaq/1n}{liri:liri/1nv}{ji:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("kiinaujalirijikkunnut", arrayOf("{kiinauja:kiinaujaq/1n}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("kiinaujalirijikkut", arrayOf("{kiinauja:kiinaujaq/1n}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("kiinaujalirinirmut", arrayOf("{kiinauja:kiinaujaq/1n}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("kiinaujani", arrayOf("{kiinauja:kiinaujaq/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("kiinaujanik", arrayOf("{kiinauja:kiinaujaq/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("kiinaujanit", arrayOf("{kiinauja:kiinaujaq/1n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("kiinaujanut", arrayOf("{kiinauja:kiinaujaq/1n}{nut:nut/tn-dat-p}")));

//		2020-04, BF:
//		kiinaujatigut : fréquence : 1152.
//		Variante « kiinaujaqtigut : fréquence 108.
//		D’après mes connaissances, il devrait y avoir un « q » devant « tigut »,
//		mais on rencontre cette forme plus souvent que celle que j’aurais
//		pensée correcte. Alors : je ne sais pas – encore – quoi faire avec ça,
//		d’où le possiblyMisspelledWord.
//
//		Même chose pour : maligatigut, qallunaatitut
//
        addCase(AnalyzerCase("kiinaujatigut", arrayOf("{kiinauja:kiinaujaq/1n}{tigut:tigut/tn-via-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment;"));
        addCase(AnalyzerCase("kikkulimaanut", arrayOf("{kikku:kikkut/1p}{limaa:limaaq/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("kikkulimaat", arrayOf("{kikku:kikkut/1p}{limaa:limaaq/1nn}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("kikkut", arrayOf("{kikkut:kikkut/1p}")));
        addCase(AnalyzerCase("kikkutuinnait", arrayOf("{kikku:kikkut/1p}{tuinna:tuinnaq/2nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("kikkutuinnarnik", arrayOf("{kikku:kikkut/1p}{tuinnar:tuinnaq/2nn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("kikkutuinnarnut", arrayOf("{kikku:kikkut/1p}{tuinnar:tuinnaq/2nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("kina", arrayOf("{kina:kina/1p}")));
        addCase(AnalyzerCase("kinatuinnaq", arrayOf("{kina:kina/1p}{tuinnaq:tuinnaq/2nn}")));
        addCase(AnalyzerCase("kingulliqpaami", arrayOf("{kingu:kingu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("kingulliqpaamik", arrayOf("{kingu:kingu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("kingulliqpaamit", arrayOf("{kingu:kingu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mit:mit/tn-abl-s}")));
        addCase(AnalyzerCase("kingulliqpaaq", arrayOf("{kingu:kingu/1n}{lliq:&iq/1nn}{paaq:paaq/1nn}")));
        addCase(AnalyzerCase("kingullirmi", arrayOf("{kingu:kingu/1n}{llir:&iq/1nn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("kinguniagut", arrayOf("{kinguni:kinguni/1n}{agut:ngagut/tn-via-s-4s}")));
        addCase(AnalyzerCase("kingunittinni", arrayOf("{kinguni:kinguni/1n}{ttinni:ptingni/tn-loc-s-1d}")));
        addCase(AnalyzerCase("kinngarni", arrayOf("{kinngar:kinngait/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("kisiani", arrayOf("{kisiani:kisiani/1c}")));
        addCase(AnalyzerCase("kisianili", arrayOf("{kisiani:kisiani/1c}{li:li/1q}")));
        addCase(AnalyzerCase("kisianittauq", arrayOf("{kisiani:kisiani/1c}{ttauq:ttauq/1q}")));
        addCase(AnalyzerCase("kisimi", arrayOf("{kisimi:kisimi/1c}")));
        addCase(AnalyzerCase("kisimili", arrayOf("{kisimi:kisimi/1c}{li:li/1q}")));
        addCase(AnalyzerCase("kisumut", arrayOf("{kisu:kisu/1p}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("kisunik", arrayOf("{kisu:kisu/1p}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("kisut", arrayOf("{kisut:kisut/1p}")));
        addCase(AnalyzerCase("kisutuinnait", arrayOf("{kisutuinna:kisutuinnaq/1p}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("kiujjutiit", arrayOf("{kiu:kiu/1v}{jjuti:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("kiujjutinga", arrayOf("{kiu:kiu/1v}{jjuti:ut/1vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("kiujjutit", arrayOf("{kiu:kiu/1v}{jjut:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("kiulvan", arrayOf("[decomposition:/kiulvan(kiulvan)/]"))
                .isProperName());
        addCase(AnalyzerCase("kiulvin", arrayOf("[decomposition:/kiulvin(kiulvin)/]"))
                .isProperName());
        addCase(AnalyzerCase("kiuvan", arrayOf("[decomposition:/kiuvan(kiuvan)/]"))
                .isProperName());
        addCase(AnalyzerCase("kivallirmi", arrayOf("{kivallir:kivalliq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("kivallirmut", arrayOf("{kivallir:kivalliq/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("kivan", arrayOf("[decomposition:/kivan(kivan)/]"))
                .isProperName());
        addCase(AnalyzerCase("kivin", arrayOf("[decomposition:/kivin(kivin)/]"))
                .isProperName());
        addCase(AnalyzerCase("kuak", arrayOf("[decomposition:/kuak(kuak)/]"))
                .isProperName());
        addCase(AnalyzerCase("kuaq", arrayOf("[decomposition:/kuaq(kuaq)/]"))
                .isProperName());
        addCase(AnalyzerCase("kuupa", null)
                .isProperName()
                .comment("Cooper"));
        addCase(AnalyzerCase("liivai", arrayOf("[decomposition:/liivai(liivai)/]"))
                .isProperName());
        addCase(AnalyzerCase("livai", arrayOf("[decomposition:/livai(livai)/]"))
                .isProperName());
//		addCase(AnalyzerCase("na", arrayOf("[decomposition:/na(na)/]"))
//			.correctDecompUnknown());
        addCase(AnalyzerCase("maajji", arrayOf("{maajji:maatsi/1n}")));
        addCase(AnalyzerCase("maani", arrayOf("{ma:ma/rad-ml}{ani:ani/tad-loc}")));
        addCase(AnalyzerCase("maanna", arrayOf("{maanna:maanna/1a}")));
        addCase(AnalyzerCase("maannakkut", arrayOf("{maanna:maanna/1a}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("maannali", arrayOf("{maanna:maanna/1a}{li:li/1q}")));
        addCase(AnalyzerCase("maannamut", arrayOf("{maanna:maanna/1a}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("maannauju", arrayOf("{maanna:maanna/1a}{u:u/1nv}{ju:juq/1vn}")));
        addCase(AnalyzerCase("maannaujukkut", arrayOf("{maanna:maanna/1a}{u:u/1nv}{ju:juq/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("maannaujumi", arrayOf("{maanna:maanna/1a}{u:u/1nv}{ju:juq/1vn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("maannaujuq", arrayOf("{maanna:maanna/1a}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("maatsi", arrayOf("{maatsi:maatsi/1n}")));
        addCase(AnalyzerCase("mai", arrayOf("{mai:mai/1e}")));
        addCase(AnalyzerCase("maik", arrayOf("[decomposition:/maik(maik)/]"))
                .isProperName());
        addCase(AnalyzerCase("makkuktunut", arrayOf("{makkuk:makkuk/1v}{tu:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("makkuktut", arrayOf("{makkuk:makkuk/1v}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("makkuttuit", arrayOf("{makkut:makkuk/1v}{tu:juq/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("makkuttunut", arrayOf("{makkut:makkuk/1v}{tu:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("maklain", arrayOf("[decomposition:/maklain(maklain)/]"))
                .isProperName());
        addCase(AnalyzerCase("makliin", arrayOf("[decomposition:/makliin(makliin)/]"))
                .isProperName());
        addCase(AnalyzerCase("makpigaq", arrayOf("{makpi:makpiq/1v}{gaq:gaq/1vn}")));
        addCase(AnalyzerCase("makua", arrayOf("{makua:makua/pd-ml-p}")));
        addCase(AnalyzerCase("maligait", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("maligaksait", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("maligaksanit", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{ksa:ksaq/1nn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("maligaksaq", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{ksaq:ksaq/1nn}")));
        addCase(AnalyzerCase("maligaliriji", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liri:liri/1nv}{ji:ji/1vn}")));
        addCase(AnalyzerCase("maligalirinirmut", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("maligaliuqti", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}")));
        addCase(AnalyzerCase("maligaliuqtii", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{i:it/tn-nom-p}")));
        addCase(AnalyzerCase("maligaliuqtiit", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("maligaliuqtikkut", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("maligaliuqtilimaanut", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{limaa:limaaq/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("maligaliuqtilimaat", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{limaa:limaaq/1nn}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("maligaliuqtimut", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("maligaliuqtinik", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("maligaliuqtinit", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("maligaliuqtinut", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("maligaliuqtit", arrayOf("{maliga:maligaq/1n}{liuq:liuq/1nv}{ti:ji/1vn}{t:it/tn-nom-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("maligaliuqtiujuq", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("maligaliuqtiujut", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("maligaliuqtiup", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("maligaliuqtiuqatiga", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{u:u/1nv}{qati:qati/1vn}{ga:ga/tn-nom-s-1s}")));
        addCase(AnalyzerCase("maligaliuqtiuqatikka", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{u:u/1nv}{qati:qati/1vn}{kka:kka/tn-nom-p-1s}")));
        addCase(AnalyzerCase("maligaliurti", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liur:liuq/1nv}{ti:ji/1vn}")));
        addCase(AnalyzerCase("maligaliurtiit", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liur:liuq/1nv}{ti:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("maligaliurtiup", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{liur:liuq/1nv}{ti:ji/1vn}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("maligaliurvik", arrayOf("{maligaliurvik:maligaliurvik/1n}")));
        addCase(AnalyzerCase("maligaliurviliarsimajut", arrayOf("{maligaliurvi:maligaliurvik/1n}{liar:liaq/2nv}{sima:sima/1vv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("maligaliurvimmi", arrayOf("{maligaliurvim:maligaliurvik/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("maligaliurvimmit", arrayOf("{maligaliurvim:maligaliurvik/1n}{mit:mit/tn-abl-s}")));
        addCase(AnalyzerCase("maligaliurvimmut", arrayOf("{maligaliurvim:maligaliurvik/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("maligaliurvinga", arrayOf("{maligaliurvi:maligaliurvik/1n}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("maligaliurvingmi", arrayOf("{maligaliurving:maligaliurvik/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("maligaliurvingmut", arrayOf("{maligaliurving:maligaliurvik/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("maligaliurviup", arrayOf("{maligaliurvi:maligaliurvik/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("maliganga", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("maligaq", arrayOf("{mali:malik/1v}{gaq:gaq/1vn}")));
        addCase(AnalyzerCase("maligarmi", arrayOf("{mali:malik/1v}{gar:gaq/1vn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("maligarmik", arrayOf("{mali:malik/1v}{gar:gaq/1vn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("maligarmut", arrayOf("{mali:malik/1v}{gar:gaq/1vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("maligarnik", arrayOf("{mali:malik/1v}{gar:gaq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("maligarnit", arrayOf("{mali:malik/1v}{gar:gaq/1vn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("maligassait", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{ssa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("maligassaq", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{ssaq:ksaq/1nn}")));
        addCase(AnalyzerCase("maligatigut", arrayOf("{maliga:maligaq/1n}{tigut:tigut/tn-via-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("maligatsait", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{tsa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("maligatsaq", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{tsaq:ksaq/1nn}")));
        addCase(AnalyzerCase("maligaujuq", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("maligaup", arrayOf("{mali:malik/1v}{ga:gaq/1vn}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("maliglugit", arrayOf("{malig:malik/1v}{lugit:lugit/tv-part-1s-3p-fut}")));
        addCase(AnalyzerCase("maliktugit", arrayOf("{malik:malik/1v}{tugit:lugit/tv-part-1s-3p-prespas}")));
        addCase(AnalyzerCase("malillugit", arrayOf("{malil:malik/1v}{lugit:lugit/tv-part-1s-3p-fut}")));
        addCase(AnalyzerCase("malillugu", arrayOf("{malil:malik/1v}{lugu:lugu/tv-part-1s-3s-fut}")));
        addCase(AnalyzerCase("malittugit", arrayOf("{malit:malik/1v}{tugit:lugit/tv-part-1s-3p-prespas}")));
        addCase(AnalyzerCase("malittugu", arrayOf("{malit:malik/1v}{tugu:lugu/tv-part-1s-3s-prespas}")));
        addCase(AnalyzerCase("mamianaq", arrayOf("{mamia:mamiak/1v}{naq:naq/2vn}")));
        addCase(AnalyzerCase("mamiappunga", arrayOf("{mamiap:mamiak/1v}{punga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("maniittuq", arrayOf("[decomposition:/maniittuq(maniittuq)/]"))
                .isProperName());
        addCase(AnalyzerCase("manna", arrayOf("{manna:manna/pd-ml-s}")));
        addCase(AnalyzerCase("mannaujuq", arrayOf("{manna:manna/pd-ml-s}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("mappigaq", arrayOf("{mappi:makpiq/1v}{gaq:gaq/1vn}")));
        addCase(AnalyzerCase("mappiqtugaq", arrayOf("{mappiq:makpiq/1v}{tu:tuq/1vv}{gaq:gaq/1vn}")));
        addCase(AnalyzerCase("marrunnik", arrayOf("{marrun:marruuk/1n}{nik:nik/tn-acc-p}"))
                .isMisspelled());
        addCase(AnalyzerCase("marruuk", arrayOf("{marruuk:marruuk/1n}")));
        addCase(AnalyzerCase("marruunni", arrayOf("{marruun:marruuk/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("marruunnik", arrayOf("{marruun:marruuk/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("mataam", arrayOf("{mataam:mataam/1n}")));
        addCase(AnalyzerCase("matuiqsinirmut", arrayOf("{matu:matu/1n}{iq:iq/1nv}{si:si/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("matuiqtauninga", arrayOf("{matu:matu/1n}{iq:iq/1nv}{ta:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("matuirutimut", arrayOf("{matu:matu/1n}{i:iq/1nv}{ruti:ut/1vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("miksaanut", arrayOf("{miksa:miksa/1n}{anut:nganut/tn-dat-s-4s}")));
        addCase(AnalyzerCase("milian", arrayOf("{milian:miliat/1n}"))
                .isBorrowedWord()
                .comment("One of the many iu renderings for 'million'"));
        addCase(AnalyzerCase("milianik", arrayOf("{milia:milian/1n}{nik:nik/tn-acc-p}"))
                .isBorrowedWord()
                .comment("One of the many iu renderings for 'million'"));

        // 2020-04, BF: Voir commentaire de 'milianik' ci-dessus.
        addCase(AnalyzerCase("milianit", arrayOf("{milia:milian/1n}{nit:nit/tn-abl-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("minisitaujuq", arrayOf("{minisita:minista/1n}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("minista", arrayOf("{minista:minista/1n}")));
        addCase(AnalyzerCase("ministaa", arrayOf("{minista:minista/1n}{a:k/tn-nom-d}")));
        addCase(AnalyzerCase("ministait", arrayOf("{minista:minista/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ministamik", arrayOf("{minista:minista/1n}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("ministamut", arrayOf("{minista:minista/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("ministanga", arrayOf("{minista:minista/1n}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("ministangannut", arrayOf("{minista:minista/1n}{ngannut:ngannut/tn-dat-s-4p}")));
        addCase(AnalyzerCase("ministangat", arrayOf("{minista:minista/1n}{ngat:ngat/tn-nom-s-4p}")));
        addCase(AnalyzerCase("ministanut", arrayOf("{minista:minista/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ministau", arrayOf("{minista:minista/1n}{u:up/tn-gen-s}")));
        addCase(AnalyzerCase("ministaujuq", arrayOf("{minista:minista/1n}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("ministaujut", arrayOf("{minista:minista/1n}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("ministaullunga", arrayOf("{minista:minista/1n}{u:u/1nv}{llunga:lunga/tv-part-1s-prespas}")));
        addCase(AnalyzerCase("ministaup", arrayOf("{minista:minista/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("mis", arrayOf("{mis:mis/1n}")));
        addCase(AnalyzerCase("missaanut", arrayOf("{missa:miksa/1n}{anut:nganut/tn-dat-s-4s}")));
        addCase(AnalyzerCase("mista", arrayOf("{mista:mista/1n}")));
        addCase(AnalyzerCase("mistu", arrayOf("{mistu:mista/1n}")));
        addCase(AnalyzerCase("mitsaanut", arrayOf("{mitsa:miksa/1n}{anut:nganut/tn-dat-s-4s}")));
        addCase(AnalyzerCase("mittimatalimmi", arrayOf("{mittimatalim:mittimatalik/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("mittimatalingmi", arrayOf("{mittimataling:mittimatalik/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("naalautikkut", arrayOf("{naala:naalak/1v}{uti:ut/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("naammappuq", arrayOf("{naammap:naamak/1v}{puq:vuq/tv-dec-3s}")));
        addCase(AnalyzerCase("naammasaqtuit", arrayOf("{naamma:naamak/1v}{saq:ksaq/2vv}{tu:juq/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("naammasaqtut", arrayOf("{naamma:naamak/1v}{saq:ksaq/2vv}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("naansi", arrayOf("[decomposition:/naansi(naansi)/]"))
                .isProperName());
        addCase(AnalyzerCase("naasautaa", arrayOf("{naasa:naasaq/1v}{uta:ut/1vn}{a:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("naasautiit", arrayOf("{naasa:naasaq/1v}{uti:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("naasautik", arrayOf("{naasa:naasaq/1v}{utik:ut/1vn}")));
        addCase(AnalyzerCase("naasautilik", arrayOf("{naasa:naasaq/1v}{uti:ut/1vn}{lik:lik/1nn}")));
        addCase(AnalyzerCase("nakit", arrayOf("{nakit:nakit/1a}")));
        addCase(AnalyzerCase("nakuqmi", arrayOf("{nakuq:nakuq/1v}{mi:miik/1vn}"))
                .isMisspelled()
                .comment("Should be 'nakurmi'"));
        addCase(AnalyzerCase("nakuqmii", arrayOf("{nakuq:nakuq/1v}{mii:miik/1vn}"))
                .isMisspelled()
                .comment("Should be 'nakurmii'"));
        addCase(AnalyzerCase("nakurmii", arrayOf("{nakur:nakuq/1v}{mii:miik/1vn}")));
        addCase(AnalyzerCase("nakurmiik", arrayOf("{nakur:nakuq/1v}{miik:miik/1vn}")));
        addCase(AnalyzerCase("naliak", arrayOf("{naliak:naliak/1p}")));
        addCase(AnalyzerCase("naliqqangit", arrayOf("{naliqqa:naliqqaq/1n}{ngit:ngit/tn-nom-p-4s}"))
                .correctDecompUnknown());

//		2020-04, BF:
//		nalliani :	Fréquence : 132. « any », « some », ...
//		La décomposition attendue proposée contient la racine nalliq/1p.
//		Celle-ci, qui serait un pronom, n’existe pas dans la base de données !
//		Spalding la présente comme une alternative à « naliak » qui signifie
//		« which one ? ».
        addCase(AnalyzerCase("nalliani", arrayOf("{nalli:nalliq/1p}{ani:ngani/tn-loc-s-4s}"))
                .correctDecompUnknown());
        addCase(AnalyzerCase("nalunanngittuq", arrayOf("{naluna:nalunak/1v}{nngit:nngit/1vv}{tuq:juq/1vn}")));
        addCase(AnalyzerCase("namminiq", arrayOf("{namminiq:nangminiq/1n}")));
        addCase(AnalyzerCase("namminiqaqtunut", arrayOf("{nammini:nangminiq/1n}{qaq:qaq/1nv}{tu:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("nangiqsivunga", arrayOf("{nangiq:nangiq/1v}{si:si/2vv}{vunga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("nangminiq", arrayOf("{nangminiq:nangminiq/1n}")));
        addCase(AnalyzerCase("nanuit", arrayOf("{nanu:nanuq/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("nanulik", arrayOf("{nanu:nanuq/1n}{lik:lik/1nn}")));
        addCase(AnalyzerCase("natsilik", arrayOf("{natsilik:nattilik/1n}")));
        addCase(AnalyzerCase("naujaani", arrayOf("{naujaa:naujaat/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("naukkut", arrayOf("{nau:nauk/1a}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("nigiani", arrayOf("{nigi:niggig/1n}{ani:ngani/tn-loc-s-4s}"))
                .isMisspelled()
                .comment("Should be 'niggiani'"));
        addCase(AnalyzerCase("nikuvippunga", arrayOf("{nikuvip:nikuvik/1v}{punga:vunga/tv-dec-1s}")));

//		2020-04, BF:
//		niqsunaqtuq : 	« honourable » Fréquence : 584.
//		Spalding a la racine « nirtur » = « niqtuq ». On trouve 1679 fois le
//		mot « niqtunaqtuq ».  Schneider a aussi la forme « niqtuq ».
//		Je ne sais pas. J’ai souvent constaté qu’il y a confusion entre « s »
//		et « t » en inuktitut, mais je ne sais pas quoi faire dans ce cas-ci,
//		de là le possiblyMisspelledWord().
//
        addCase(AnalyzerCase("niqsunaqtuq", arrayOf("{niqsu:niqtuq/1v}{naq:naq/1vv}{tuq:juq/1vn}"))
                .possiblyMisspelledWord());
        addCase(AnalyzerCase("niqtunaqtuq", arrayOf("{niqtu:niqtuq/1v}{naq:naq/1vv}{tuq:juq/1vn}")));
        addCase(AnalyzerCase("niriuppugut", arrayOf("{niriup:niriuk/1v}{pugut:vugut/tv-dec-1p}")));
        addCase(AnalyzerCase("niriuppunga", arrayOf("{niriup:niriuk/1v}{punga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("nirtunartuq", arrayOf("{nirtu:niqtuq/1v}{nar:naq/1vv}{tuq:juq/1vn}")));
        addCase(AnalyzerCase("niruaqtulirinirmut", arrayOf("{niruaq:niruaq/1v}{tu:juq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("nunaliit", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("nunalilimaat", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{limaa:limaaq/1nn}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("nunalinginni", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{nginni:nginni/tn-loc-p-4s}")));
        addCase(AnalyzerCase("nunalingni", arrayOf("{nuna:nuna/1n}{ling:lik/1nn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("nunalingnit", arrayOf("{nuna:nuna/1n}{ling:lik/1nn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("nunalingnut", arrayOf("{nuna:nuna/1n}{ling:lik/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("nunalinni", arrayOf("{nuna:nuna/1n}{lin:lik/1nn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("nunalinnik", arrayOf("{nuna:nuna/1n}{lin:lik/1nn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("nunalinnit", arrayOf("{nuna:nuna/1n}{lin:lik/1nn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("nunalinnu", arrayOf("{nuna:nuna/1n}{lin:lik/1nn}{nu:nut/tn-dat-p}")));
        addCase(AnalyzerCase("nunalinnut", arrayOf("{nuna:nuna/1n}{lin:lik/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("nunalittinni", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{ttinni:ptingni/tn-loc-s-1d}")));
        addCase(AnalyzerCase("nunaliujuni", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{u:u/1nv}{ju:juq/1vn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("nunaliujunik", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{u:u/1nv}{ju:juq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("nunaliujunit", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{u:u/1nv}{ju:juq/1vn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("nunaliujunut", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{u:u/1nv}{ju:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("nunaliujuq", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("nunaliujut", arrayOf("{nuna:nuna/1n}{li:lik/1nn}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("nunami", arrayOf("{nuna:nuna/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("nunanganni", arrayOf("{nuna:nuna/1n}{nganni:nganni/tn-loc-s-4p}")));
        addCase(AnalyzerCase("nunangannut", arrayOf("{nuna:nuna/1n}{ngannut:ngannut/tn-dat-s-4p}")));
        addCase(AnalyzerCase("nunatsiap", arrayOf("{nunatsia:nunatsiaq/1n}{p:up/tn-gen-s}")));
        addCase(AnalyzerCase("nunatsiarmi", arrayOf("{nunatsiar:nunatsiaq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("nunattiap", arrayOf("{nunattia:nunatsiaq/1n}{p:up/tn-gen-s}")));
        addCase(AnalyzerCase("nunattinni", arrayOf("{nuna:nuna/1n}{ttinni:ptingni/tn-loc-s-1d}")));
        addCase(AnalyzerCase("nunavu", arrayOf("{nunavu:nunavut/1n}")));
        addCase(AnalyzerCase("nunavulimaami", arrayOf("{nunavu:nunavut/1n}{limaa:limaaq/1nn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("nunavumi", arrayOf("{nunavu:nunavut/1n}{mi:mi/tn-loc-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("nunavumiunut", arrayOf("{nunavu:nunavut/1n}{miu:miuq/1nn}{nut:nut/tn-dat-p}"))
                .isMisspelled());
        addCase(AnalyzerCase("nunavumiut", arrayOf("{nunavu:nunavut/1n}{miu:miuq/1nn}{t:it/tn-nom-p}"))
                .isMisspelled());
        addCase(AnalyzerCase("nunavummi", arrayOf("{nunavum:nunavut/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("nunavummiunut", arrayOf("{nunavum:nunavut/1n}{miu:miuq/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("nunavummiut", arrayOf("{nunavum:nunavut/1n}{miu:miuq/1nn}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("nunavummut", arrayOf("{nunavum:nunavut/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("nunavumut", arrayOf("{nunavu:nunavut/1n}{mut:mut/tn-dat-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("nunavungmi", arrayOf("{nunavung:nunavut/1n}{mi:mi/tn-loc-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("nunavut", arrayOf("{nunavut:nunavut/1n}")));
        addCase(AnalyzerCase("nunavutmi", arrayOf("{nunavut:nunavut/1n}{mi:mi/tn-loc-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("nunavuumi", arrayOf("{nunavuu:nunavut/1n}{mi:mi/tn-loc-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("nunavuumik", arrayOf("{nunavum:nunavut/1n}{mik:mik/tn-acc-s}")).isMisspelled());
        addCase(AnalyzerCase("nunavuumit", arrayOf("{nunavum:nunavut/1n}{mit:mit/tn-abl-s}")).isMisspelled());
        addCase(AnalyzerCase("nunavuumut", arrayOf("{nunavum:nunavut/1n}{mut:mut/tn-dat-s}")).isMisspelled());
        addCase(AnalyzerCase("nunavuup", arrayOf("{nunavu:nunavut/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("nunavuut", arrayOf("{nunavu:nunavut/1n}{ut:up/tn-gen-s}"))
                .isMisspelled());

//		2020-04, BF:
//		nunnguani :	« its end »
//		Spalding a la racine verbale « nungut » qui signifie « to be used up,
//		consumed, worn away, erased », ce qui a un lien ténu mais plausible
//		avec « end ». Mais « ani » est une terminaison nominale, or il n’y a
//		pas de pronom « nungu... » ou « nunngu... » ni dans Spalding, ni dans
//		Schneider. Je ne sais donc pas quoi faire avec ça, d’où le
//		correctDecompUnknown()
//
        addCase(AnalyzerCase("nunnguani", arrayOf("{nunngu:nunnguq/1n}{ani:ngani/tn-loc-s-4s}"))
                .correctDecompUnknown());
        addCase(AnalyzerCase("nuqqaqpuq", arrayOf("{nuqqaq:nuqqaq/1v}{puq:vuq/tv-dec-3s}")));
        addCase(AnalyzerCase("nutaami", arrayOf("{nutaa:nutaaq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("nutaamik", arrayOf("{nutaa:nutaaq/1n}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("nutaamut", arrayOf("{nutaa:nutaaq/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("nutaanik", arrayOf("{nutaa:nutaaq/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("nutaanit", arrayOf("{nutaa:nutaaq/1n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("nutaanut", arrayOf("{nutaa:nutaaq/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("nutaaq", arrayOf("{nutaaq:nutaaq/1n}")));
        addCase(AnalyzerCase("nutaat", arrayOf("{nutaa:nutaaq/1n}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("nutarak", arrayOf("[decomposition:/nutarak(nutarak)/]")).isProperName());
        addCase(AnalyzerCase("nutaraq", arrayOf("[decomposition:/nutaraq(nutaraq)/]")).isProperName());
        addCase(AnalyzerCase("pa", arrayOf("[decomposition:/pa(pa)/]")).isProperName());
        addCase(AnalyzerCase("paal", arrayOf("[decomposition:/paal(paal)/]"))
                .isProperName());
        addCase(AnalyzerCase("paanapaasi", null)
                .isProperName()
                .comment("Barnabas"));
        addCase(AnalyzerCase("paanapas", null)
                .isProperName()
                .comment("Barnabas"));

//		2020-04, BF:
//		paktaqtuqtut :	« Applause » Fréquence : 179.
//		Spalding n’a pas de racine verbale « paktak ». Il a par contre une
//		racine « patik » qui, ajouté le suffixe fréquentatif « taq », signifie
//		« to clap hands together in ovation or approval », ce qui est
//		effectivement des applaudissements. Schneider a également « patik »
//		avec le même sens, et aussi « pattak » (to slap with the palm of one’s
//		hand). En y ajoutant le fréquentatif « tuq » (autre forme de
//		fréquentatif équivalente à « taq », on pourrait expliquer le
//		mot « paktaqtuqtut ». Mais le mot a « paktaQ » et non
//		« paktaK ». Je ne sais donc pas quoi faire avec ça actuellement.
        addCase(AnalyzerCase("paktaqtuqtut", arrayOf("{paktak:paktak/1v}{tuq:tuq/1vv}{tut:jut/tv-ger-3p}"))
                .correctDecompUnknown());
        addCase(AnalyzerCase("paliisikkut", arrayOf("{paliisi:paliisi/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("panniqtuumi", arrayOf("{panniqtuu:pangnirtuuq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("panniqtuuq", arrayOf("{panniqtuuq:pangnirtuuq/1n}")));
        addCase(AnalyzerCase("parnaut", arrayOf("{parna:parnaq/1v}{ut:ut/1vn}")));
        addCase(AnalyzerCase("parnautiit", arrayOf("{parna:parnaq/1v}{uti:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("parnautinik", arrayOf("{parna:parnaq/1v}{uti:ut/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("pattatuqtut", arrayOf("{patta:paktak/1v}{tuq:tuq/1vv}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("paul", arrayOf("[decomposition:/paul(paul)/]"))
                .isProperName());
        addCase(AnalyzerCase("paulusi", arrayOf("[decomposition:/paulusi(paulusi)/]"))
                .isProperName());
        addCase(AnalyzerCase("pi", arrayOf("[decomposition:/pi(pi)/]"))
                .isProperName());
        addCase(AnalyzerCase("pigiaqtitait", arrayOf("{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{ta:jaq/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("pigiaqtitamut", arrayOf("{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{ta:jaq/1vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("pigiaqtitaq", arrayOf("{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{taq:jaq/1vn}")));
        addCase(AnalyzerCase("pigiaqtitat", arrayOf("{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{ta:jaq/1vn}{t:it/tn-nom-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("pigiaqtitaujuq", arrayOf("{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{ta:jaq/1vn}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("pigiarutiksanit", arrayOf("{pi:pi/1v}{gia:giaq/1vv}{ruti:ut/1vn}{ksa:ksaq/1nn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("piiku", null)
                .isProperName()
                .comment("Picco"));
        addCase(AnalyzerCase("piikuu", null)
                .isProperName()
                .comment("Picco"));
        addCase(AnalyzerCase("piita", null)
                .isProperName()
                .comment("Peter"));
        addCase(AnalyzerCase("pijjutigillugit", arrayOf("{pi:pi/1v}{jjuti:jjut/1vn}{gi:gi/1nv}{llugit:lugit/tv-part-1s-3p-prespas}")));
        addCase(AnalyzerCase("pijjutigillugu", arrayOf("{pi:pi/1v}{jjuti:jjut/1vn}{gi:gi/1nv}{llugu:lugu/tv-part-1s-3s-prespas}")));
        addCase(AnalyzerCase("pijjutilik", arrayOf("{pi:pi/1v}{jjuti:jjut/1vn}{lik:lik/1nn}")));
        addCase(AnalyzerCase("pijjutiqaqtunik", arrayOf("{pi:pi/1v}{jjuti:jjut/1vn}{qaq:qaq/1nv}{tu:juq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("pijjutiqaqtuq", arrayOf("{pi:pi/1v}{jjuti:jjut/1vn}{qaq:qaq/1nv}{tuq:juq/1vn}")));
        addCase(AnalyzerCase("pijjutiqaqtut", arrayOf("{pi:pi/1v}{jjuti:jjut/1vn}{qaq:qaq/1nv}{tut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("piku", null)
                .isProperName()
                .comment("Picco"));
        addCase(AnalyzerCase("piliriaksat", arrayOf("{piliria:piliriaq/1n}{ksa:ksaq/1nn}{t:it/tn-nom-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("piliriamik", arrayOf("{piliria:piliriaq/1n}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("piliriamut", arrayOf("{piliria:piliriaq/1n}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("piliriangujuq", arrayOf("{piliria:piliriaq/1n}{ngu:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("piliriangujut", arrayOf("{piliria:piliriaq/1n}{ngu:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("pilirianik", arrayOf("{piliria:piliriaq/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("pilirianut", arrayOf("{piliria:piliriaq/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("piliriaq", arrayOf("{piliriaq:piliriaq/1n}")));
        addCase(AnalyzerCase("piliriat", arrayOf("{piliria:piliriaq/1n}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("piliriatsaq", arrayOf("{piliria:piliriaq/1n}{tsaq:ksaq/1nn}")));
        addCase(AnalyzerCase("piliriviit", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vi:vik/3vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("pilirivik", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vik:vik/3vn}")));
        addCase(AnalyzerCase("pilirivinga", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vi:vik/3vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("piliriviujunut", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vi:vik/3vn}{u:u/1nv}{ju:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("piliriviujuq", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vi:vik/3vn}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("piliriviujut", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vi:vik/3vn}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("piliriviup", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vi:vik/3vn}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("pilirivviit", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vvi:vik/3vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("pilirivvik", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vvik:vik/3vn}")));
        addCase(AnalyzerCase("pilirivvinga", arrayOf("{pi:pi/1n}{liri:liri/1nv}{vvi:vik/3vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("pillugit", arrayOf("{pi:pi/1v}{llugit:lugit/tv-part-1s-3p-prespas}")));
        addCase(AnalyzerCase("pillugu", arrayOf("{pi:pi/1v}{llugu:lugu/tv-part-1s-3s-prespas}")));
        addCase(AnalyzerCase("piluaqtumi", arrayOf("{pi:pi/1v}{luaq:luaq/1vv}{tu:juq/1vn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("piluaqtumik", arrayOf("{pi:pi/1v}{luaq:luaq/1vv}{tu:juq/1vn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("piluaqtumit", arrayOf("{pi:pi/1v}{luaq:luaq/1vv}{tu:juq/1vn}{mit:mit/tn-abl-s}")));
        addCase(AnalyzerCase("pinasuarusiulauqtumi", arrayOf("{pinasuarusi:pinasuarusiq/1n}{u:u/1nv}{lauq:lauq/1vv}{tu:juq/1vn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("pinasuarusiup", arrayOf("{pinasuarusi:pinasuarusiq/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("pingajuanni", arrayOf("{pingajuan:pingajuat/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("pingajuannik", arrayOf("{pingajuan:pingajuat/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("pingajuannit", arrayOf("{pingajuan:pingajuat/1n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("pingajuat", arrayOf("{pingajuat:pingajuat/1n}")));
        addCase(AnalyzerCase("pingasunik", arrayOf("{pingasu:pingasu/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("pingasunit", arrayOf("{pingasu:pingasu/1n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("pingasunut", arrayOf("{pingasu:pingasu/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("pingasut", arrayOf("{pingasut:pingasut/1n}")));
        addCase(AnalyzerCase("piqujaksait", arrayOf("{pi:pi/1v}{qu:qu/2vv}{ja:jaq/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("piqujaksaq", arrayOf("{pi:pi/1v}{qu:qu/2vv}{ja:jaq/1vn}{ksaq:ksaq/1nn}")));
        addCase(AnalyzerCase("piqujaksat", arrayOf("{pi:pi/1v}{qu:qu/2vv}{ja:jaq/1vn}{ksa:ksaq/1nn}{t:it/tn-nom-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("piqujaq", arrayOf("{pi:pi/1v}{qu:qu/2vv}{jaq:jaq/1vn}")));
        addCase(AnalyzerCase("pivalliatittinirmut", arrayOf("{pi:pi/1v}{vallia:vallia/1vv}{tit:tit/1vv}{ti:si/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("pu", arrayOf("[decomposition:/pu(pu)/]"))
                .isProperName());
        addCase(AnalyzerCase("pukiqnaq", arrayOf("[decomposition:/pukiqnaq(pukiqnaq)/]"))
                .isProperName());
        addCase(AnalyzerCase("pukirnak", arrayOf("[decomposition:/pukirnak(pukirnak)/]"))
                .isProperName());
        addCase(AnalyzerCase("pukirnaq", arrayOf("[decomposition:/pukirnaq(pukirnaq)/]"))
                .isProperName());
        addCase(AnalyzerCase("pukirngnak", arrayOf("[decomposition:/pukirngnak(pukirngnak)/]"))
                .isProperName());
        addCase(AnalyzerCase("pukkirnaq", arrayOf("[decomposition:/pukkirnaq(pukkirnaq)/]"))
                .isProperName());
        addCase(AnalyzerCase("pulaaqtinit", arrayOf("{pulaaq:pulaaq/1v}{ti:ji/1vn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("pulaariaqsimajut", arrayOf("{pulaa:pulaaq/1v}{riaq:giaq/1vv}{sima:sima/1vv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("pularaqtulirinirmut", arrayOf("{pula:pula/1v}{raq:raq/1vv}{tu:juq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("puqirnak", arrayOf("[decomposition:/puqirnak(puqirnak)/]"))
                .isProperName());
        addCase(AnalyzerCase("puriimmia", arrayOf("{puriimmia:puriimmia/1n}")));
        addCase(AnalyzerCase("qallunaat", arrayOf("{qallunaa:qaplunaaq/1n}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("qallunaatitut", arrayOf("{qallunaaq:qaplunaaq/1n}{titut:titut/tn-sim-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("qamaniqtuaq", arrayOf("{qamaniqtuaq:qamaniqjuaq/1n}")));
        addCase(AnalyzerCase("qamaniqtuarmi", arrayOf("{qamaniqtuar:qamaniqjuaq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("qamanittuarmi", arrayOf("{qamanittuar:qamaniqjuaq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("qanga", arrayOf("{qanga:qanga/1a}")));
        addCase(AnalyzerCase("qangakkut", arrayOf("{qanga:qanga/1a}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("qanu", arrayOf("{qanu:qanuq/1a}")));
        addCase(AnalyzerCase("qanuimmat", arrayOf("{qanuim:qanuit/1v}{mat:mat/tv-caus-4s}")));
        addCase(AnalyzerCase("qanuittuni", arrayOf("{qanu:qanuq/1a}{it:it/3nv}{tu:juq/1vn}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("qanuittunik", arrayOf("{qanu:qanuq/1a}{it:it/3nv}{tu:juq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("qanuq", arrayOf("{qanuq:qanuq/1a}")));
        addCase(AnalyzerCase("qanurli", arrayOf("{qanur:qanuq/1a}{li:li/1q}")));
        addCase(AnalyzerCase("qanutuinnaq", arrayOf("{qanu:qanuq/1a}{tuinnaq:tuinnaq/2nn}")));

        // 2020-04, BF:
//		qattinik :	on a vu pendant le contrat que ce mot serait en fait basé
//		sur la racine « qapsi » et qu’il manquerait alors quelque chose dans la
//		base de données pour expliquer et accepter la forme
//		« qatti » = « qapsi ». Comme je ne connais pas encore le lien entre les
//		deux, d'ou le correctDecompUnknown()
//
        addCase(AnalyzerCase("qattinik", arrayOf("{qatti:qapsit/1n}{nit:nit/tn-acc-p}"))
                .correctDecompUnknown());
        addCase(AnalyzerCase("qaujigiarutit", arrayOf("{qauji:qauji/1v}{gia:giaq/1vv}{rut:ut/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("qaujigumajunga", arrayOf("{qauji:qauji/1v}{guma:juma/1vv}{junga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("qaujijumajunga", arrayOf("{qauji:qauji/1v}{juma:juma/1vv}{junga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("qaujijumatuinnaqpunga", arrayOf("{qauji:qauji/1v}{juma:juma/1vv}{tuinnaq:tuinnaq/1vv}{punga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("qaujijumatuinnaqtunga", arrayOf("{qauji:qauji/1v}{juma:juma/1vv}{tuinnaq:tuinnaq/1vv}{tunga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("qaujijumavunga", arrayOf("{qauji:qauji/1v}{juma:juma/1vv}{vunga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("qaujimagama", arrayOf("{qaujima:qaujima/1v}{gama:gama/tv-caus-1s}")));
        addCase(AnalyzerCase("qaujimagatta", arrayOf("{qaujima:qaujima/1v}{gatta:gapta/tv-caus-1p}")));
        addCase(AnalyzerCase("qaujimagavit", arrayOf("{qaujima:qaujima/1v}{gavit:gavit/tv-caus-2s}")));
        addCase(AnalyzerCase("qaujimajatuqanginnik", arrayOf("{qaujima:qaujima/1v}{ja:jaq/1vn}{tuqa:tuqaq/1nn}{nginnik:nginnik/tn-acc-p-4s}")));
        addCase(AnalyzerCase("qaujimajatuqanginnit", arrayOf("{qaujima:qaujima/1v}{ja:jaq/1vn}{tuqa:tuqaq/1nn}{nginnit:nginnit/tn-abl-p-4s}")));
        addCase(AnalyzerCase("qaujimajatuqanginnut", arrayOf("{qaujima:qaujima/1v}{ja:jaq/1vn}{tuqa:tuqaq/1nn}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("qaujimajatuqangit", arrayOf("{qaujima:qaujima/1v}{ja:jaq/1vn}{tuqa:tuqaq/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("qaujimajuinnaugatta", arrayOf("{qaujima:qaujima/1v}{ju:juq/1vn}{inna:innaq/1nn}{u:u/1nv}{gatta:gapta/tv-caus-1p}")));
        addCase(AnalyzerCase("qaujimajunga", arrayOf("{qaujima:qaujima/1v}{junga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("qaujimallunga", arrayOf("{qaujima:qaujima/1v}{llunga:lunga/tv-part-1s-prespas}")));
        addCase(AnalyzerCase("qaujimammata", arrayOf("{qaujima:qaujima/1v}{mmata:mata/tv-caus-4p}")));
        addCase(AnalyzerCase("qaujimanngilanga", arrayOf("{qaujima:qaujima/1v}{nngi:nngit/1vv}{langa:langa/tv-dec-1s}")));
        addCase(AnalyzerCase("qaujimanngittunga", arrayOf("{qaujima:qaujima/1v}{nngit:nngit/1vv}{tunga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("qaujimavugut", arrayOf("{qaujima:qaujima/1v}{vugut:vugut/tv-dec-1p}")));
        addCase(AnalyzerCase("qaujimavunga", arrayOf("{qaujima:qaujima/1v}{vunga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("qaujisarniq", arrayOf("{qauji:qauji/1v}{sar:saq/1vv}{niq:niq/2vn}")));
        addCase(AnalyzerCase("qaujisarnirmut", arrayOf("{qauji:qauji/1v}{sar:saq/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("qauppat", arrayOf("{qauppat:qaukpat/1n}")));
        addCase(AnalyzerCase("qautamaamut", arrayOf("{qau:qau/1n}{tamaa:tamaaq/1nn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("qautamaat", arrayOf("{qau:qau/1n}{tamaa:tamaaq/1nn}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("qikiqtaalummi", arrayOf("{qikiqtaalum:qikiqtaaluk/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("qikiqtaalungmi", arrayOf("{qikiqtaalung:qikiqtaaluk/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("qikiqtaaluup", arrayOf("{qikiqtaalu:qikiqtaaluk/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("qilavva", arrayOf("[decomposition:/qilavva(qilavva)/]"))
                .isProperName());
        addCase(AnalyzerCase("qilavvak", arrayOf("[decomposition:/qilavvak(qilavvak)/]")).isProperName());
        addCase(AnalyzerCase("qimirrujiit", arrayOf("{qimirru:qimirru/1v}{ji:ji/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("qimirruniq", arrayOf("{qimirru:qimirru/1v}{niq:niq/2vn}")));
        addCase(AnalyzerCase("qingaummi", arrayOf("{qingaum:qingaq/2n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("qitingani", arrayOf("{qiti:qitiq/1n}{ngani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("qitirmiuni", arrayOf("{qitirmiu:qitirmiut/1n}{ni:ni/tn-loc-p}")));
        addCase(AnalyzerCase("qitirmiunit", arrayOf("{qitirmiu:qitirmiut/1n}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("qitirmiunut", arrayOf("{qitirmiu:qitirmiut/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("qitirmiut", arrayOf("{qitirmiut:qitirmiut/1n}")));
        addCase(AnalyzerCase("qujannami", arrayOf("{quja:quja/1v}{nna:naq/1vv}{mi:miik/1vn}")));
        addCase(AnalyzerCase("qujannamii", arrayOf("{qujannamii:qujannamiik/1a}")));
        addCase(AnalyzerCase("qujannamiik", arrayOf("{qujannamiik:qujannamiik/1a}")));
        addCase(AnalyzerCase("qujannamiingujutit", arrayOf("{qujannamii:qujannamiik/1a}{ngu:u/1nv}{jutit:jutit/tv-ger-2s}")));
        addCase(AnalyzerCase("qujannamiirumajakka", arrayOf("{qujannamii:qujannamiiq/1v}{ruma:juma/1vv}{jakka:jakka/tv-ger-1s-3p}")));
        addCase(AnalyzerCase("qujannamiirumajara", arrayOf("{qujannamii:qujannamiiq/1v}{ruma:juma/1vv}{jara:jara/tv-ger-1s-3s}")));
        addCase(AnalyzerCase("qujannamiirumavakka", arrayOf("{qujannamii:qujannamiiq/1v}{ruma:juma/1vv}{vakka:vakka/tv-dec-1s-3p}")));
        addCase(AnalyzerCase("qujannamiirumavara", arrayOf("{qujannamii:qujannamiiq/1v}{ruma:juma/1vv}{vara:vara/tv-dec-1s-3s}")));
        addCase(AnalyzerCase("qujannamik", arrayOf("{quja:quja/1v}{nna:naq/1vv}{mik:miik/1vn}"))
                .isMisspelled());
        addCase(AnalyzerCase("qulluktuq", arrayOf("{qulluktuq:kugluktuk/1n}")));
        addCase(AnalyzerCase("quppirniliit", arrayOf("{quppir:quppiq/1v}{ni:niq/2vn}{li:lik/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("qurluqtuumi", arrayOf("{qurluqtuu:kugluktuk/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("quviappunga", arrayOf("{quviap:quviak/1v}{punga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("quviasukpunga", arrayOf("{quvia:quviak/1v}{suk:suk/1vv}{punga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("quviattunga", arrayOf("{quviat:quviak/1v}{tunga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("riipika", null)
                .isProperName()
                .comment("Rebecca"));
        addCase(AnalyzerCase("saiman", null)
                .isProperName()
                .comment("Simon"));
        addCase(AnalyzerCase("sanajulirijikkunnut", arrayOf("{sana:sana/1v}{ju:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("sanajulirijikkut", arrayOf("{sana:sana/1v}{ju:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("saniani", arrayOf("{sani:sani/1n}{ani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("sanikiluaq", arrayOf("{sanikiluaq:sanikiluaq/1n}")));
        addCase(AnalyzerCase("sanikiluarmi", arrayOf("{sanikiluar:sanikiluaq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("saqqitaujuq", arrayOf("{saqqi:saqqik/1v}{ta:jaq/1vn}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("saqqitaujut", arrayOf("{saqqi:saqqik/1v}{ta:jaq/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("saqqitauningit", arrayOf("{saqqi:saqqik/1v}{ta:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("saqqitausimajuq", arrayOf("{saqqi:saqqik/1v}{ta:jaq/1vn}{u:u/1nv}{sima:sima/1vv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("silataani", arrayOf("{silata:silata/1n}{ani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("silaup", arrayOf("{sila:sila/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("sivuliqti", arrayOf("{sivuliqti:sivuliqti/1n}")));
        addCase(AnalyzerCase("sivuliqtii", arrayOf("{sivuliqti:sivuliqti/1n}{i:k/tn-nom-d}")));
        addCase(AnalyzerCase("sivuliqtiit", arrayOf("{sivuliqti:sivuliqti/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("sivuliqtinginni", arrayOf("{sivuliqti:sivuliqti/1n}{nginni:nginni/tn-loc-p-4s}")));
        addCase(AnalyzerCase("sivuliqtiup", arrayOf("{sivuliqti:sivuliqti/1n}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("sivuliuqti", arrayOf("{sivuliuqti:sivuliuqti/1n}")));
        addCase(AnalyzerCase("sivuliuqtii", arrayOf("{sivuliuqti:sivuliuqti/1n}{i:k/tn-nom-d}")));
        addCase(AnalyzerCase("sivulliqpaami", arrayOf("{sivu:sivu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("sivulliqpaamik", arrayOf("{sivu:sivu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("sivulliqpaamit", arrayOf("{sivu:sivu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mit:mit/tn-abl-s}")));
        addCase(AnalyzerCase("sivulliqpaaq", arrayOf("{sivu:sivu/1n}{lliq:&iq/1nn}{paaq:paaq/1nn}")));
        addCase(AnalyzerCase("sivullirmi", arrayOf("{sivu:sivu/1n}{llir:&iq/1nn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("sivullirmik", arrayOf("{sivu:sivu/1n}{llir:&iq/1nn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("sivuniagut", arrayOf("{sivuni:sivuni/1n}{agut:ngagut/tn-via-s-4s}")));
        addCase(AnalyzerCase("sivuniani", arrayOf("{sivuni:sivuni/1n}{ani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("sivuniksami", arrayOf("{sivuni:sivuni/1n}{ksa:ksaq/1nn}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("sivuningani", arrayOf("{sivuni:sivuni/1n}{ngani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("sivunittinni", arrayOf("{sivuni:sivuni/1n}{ttinni:ptingni/tn-loc-s-1d}")));
        addCase(AnalyzerCase("suli", arrayOf("{suli:suli/1a}")));
        addCase(AnalyzerCase("sulijuq", arrayOf("{suli:suli/1v}{juq:juq/1vn}")));
        addCase(AnalyzerCase("sulikkanniiq", arrayOf("{suli:suli/1a}{kkanniiq:kkanniq/1nn}"))
                .isMisspelled());
        addCase(AnalyzerCase("sulikkanniq", arrayOf("{suli:suli/1a}{kkanniq:kkanniq/1nn}")));
        addCase(AnalyzerCase("summat", arrayOf("{su:su/1v}{mmat:mat/tv-caus-4s}")));
        addCase(AnalyzerCase("surlu", arrayOf("{surlu:suurlu/1a}"))
                .isMisspelled());
        addCase(AnalyzerCase("surusiit", arrayOf("{surusi:surusiq/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("surusirnut", arrayOf("{surusir:surusiq/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("suuqaimma", arrayOf("{suuqaimma:suuqaimma/1e}")));
        addCase(AnalyzerCase("suurlu", arrayOf("{suurlu:suurlu/1a}")));
        addCase(AnalyzerCase("suusan", arrayOf("[decomposition:/suusan(suusan)/]"))
                .isProperName());
        addCase(AnalyzerCase("taakkua", arrayOf("{taakkua:taakkua/pd-sc-p}")));
        addCase(AnalyzerCase("taakkunani", arrayOf("{taakku:taakku/rpd-sc-p}{nani:nani/tpd-loc-p}")));
        addCase(AnalyzerCase("taakkunanngat", arrayOf("{taakku:taakku/rpd-sc-p}{nanngat:nanngat/tpd-abl-p}")));
        addCase(AnalyzerCase("taakkuninga", arrayOf("{taakku:taakku/rpd-sc-p}{ninga:ninga/tpd-acc-p}")));
        addCase(AnalyzerCase("taakkununga", arrayOf("{taakku:taakku/rpd-sc-p}{nunga:nunga/tpd-dat-p}")));
        addCase(AnalyzerCase("taaksumunga", arrayOf("taaksumunga taaksumunga {taaksu:taangna/rpd-ml-s}{munga:munga/tpd-dat-s}")));
        addCase(AnalyzerCase("taalait", arrayOf("{taala:taala/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("taampsan", arrayOf("[decomposition:/taampsan(taampsan)/]"))
                .isProperName());
        addCase(AnalyzerCase("taamsan", arrayOf("[decomposition:/taamsan(taamsan)/]"))
                .isProperName());
        addCase(AnalyzerCase("taanna", arrayOf("{taanna:taamna/pd-sc-s}")));
        addCase(AnalyzerCase("taanut", null)
                .isProperName()
                .comment("Donald"));
        addCase(AnalyzerCase("taassuma", arrayOf("{taassu:taapsu/rpd-sc-s}{ma:ma/tpd-gen-s}")));
        addCase(AnalyzerCase("taassuminga", arrayOf("{taassu:taapsu/rpd-sc-s}{minga:minga/tpd-acc-s}")));
        addCase(AnalyzerCase("taassumunga", arrayOf("{taassu:taapsu/rpd-sc-s}{munga:munga/tpd-dat-s}")));
        addCase(AnalyzerCase("taatsuma", arrayOf("{taatsu:taapsu/rpd-sc-s}{ma:ma/tpd-gen-s}")));
        addCase(AnalyzerCase("taatsuminga", arrayOf("{taatsu:taapsu/rpd-sc-s}{minga:minga/tpd-acc-s}")));
        addCase(AnalyzerCase("tagvani", arrayOf("{tagv:tagv/rad-sc}{ani:ani/tad-loc}")));
        addCase(AnalyzerCase("taikani", arrayOf("{taik:taik/rad-sc}{ani:ani/tad-loc}")));
        addCase(AnalyzerCase("taikkua", arrayOf("{taikkua:taikkua/pd-sc-p}")));
        addCase(AnalyzerCase("taikkuninga", arrayOf("{taikku:taikku/rpd-sc-p}{ninga:ninga/tpd-acc-p}")));
        addCase(AnalyzerCase("taikkununga", arrayOf("{taikku:taikku/rpd-sc-p}{nunga:nunga/tpd-dat-p}")));
        addCase(AnalyzerCase("taiksumani", arrayOf("{taiksu:taiksu/rpd-sc-s}{mani:mani/tpd-loc-s}")));
        addCase(AnalyzerCase("taikua", arrayOf("{taikua:taikkua/pd-sc-p}"))
                .isMisspelled());
        addCase(AnalyzerCase("taikunga", arrayOf("{taik:taik/rad-sc}{unga:unga/tad-dat}")));
        addCase(AnalyzerCase("taima", arrayOf("{taima:taima/1a}")));
        addCase(AnalyzerCase("taimaak", arrayOf("{taimaak:taimaak/1a}")));
        addCase(AnalyzerCase("taimaimmat", arrayOf("{taimaim:taimait/1v}{mat:mat/tv-caus-4s}")));
        addCase(AnalyzerCase("taimainninganut", arrayOf("{taimain:taimait/1v}{ni:niq/2vn}{nganut:nganut/tn-dat-s-4s}")));
        addCase(AnalyzerCase("taimaittumik", arrayOf("{taimait:taimait/1v}{tu:juq/1vn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("taimaittunik", arrayOf("{taimait:taimait/1v}{tu:juq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("taimak", arrayOf("{taimak:taimaak/1a}"))
                .isMisspelled());
        addCase(AnalyzerCase("taimali", arrayOf("{taima:taima/1a}{li:li/1q}")));
        addCase(AnalyzerCase("taimaliqai", arrayOf("{taima:taima/1a}{li:li/1q}{qai:qai/1q}")));
        addCase(AnalyzerCase("taimanna", arrayOf("{taimanna:taimanna/1a}")));
        addCase(AnalyzerCase("taimannak", arrayOf("{taimannak:taimanna/1a}"))
                .isMisspelled());
        addCase(AnalyzerCase("taimannaummat", arrayOf("{taimanna:taimanna/1a}{u:u/1nv}{mmat:mat/tv-caus-4s}")));

        // 2020-04, BF:
//		taimannganit :	« since (then) » Fréquence : 203
//		Spalding et Schneider ont « taimanngat » (187 dans les Hansard),
//		adverbe. « nit » pourrait être la terminaison nominale plurielle, mais
//		je ne vois pas pourquoi on pourrait ajouter une terminaison nominale
//		plurielle à cet adverbe. Peut-être que ça s’explique : « nit » signifie
//		« from », qui a un lien avec « since » ; et le ’t’ final de l’adverbe
//		pourrait avoir un lien avec le pluriel ? De là le @. Si je pouvais
//		expliquer le lien avec la terminaison « nit », on pourrait
//		enlever le correctDecompUnknown().
//
        addCase(AnalyzerCase("taimannganit", arrayOf("{taimanngat:taimanngat/1a}{nit:nit/tn-abl-p}"))
                .correctDecompUnknown());
        addCase(AnalyzerCase("taimanngat", arrayOf("{taimanngat:taimanngat/1a}")));
        addCase(AnalyzerCase("tainna", arrayOf("{tainna:taingna/pd-sc-s}")));
        addCase(AnalyzerCase("taissumani", arrayOf("{taissu:taiksu/rpd-sc-s}{mani:mani/tpd-loc-s}")));
        addCase(AnalyzerCase("taitsumani", arrayOf("{taitsu:taiksu/rpd-sc-s}{mani:mani/tpd-loc-s}")));
        addCase(AnalyzerCase("taiviti", null)
                .isProperName()
                .comment("David"));
        addCase(AnalyzerCase("takkua", arrayOf("{takkua:taakkua/pd-sc-p}"))
                .isMisspelled());
        addCase(AnalyzerCase("tallimanut", arrayOf("{tallima:tallima/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("tamaani", arrayOf("{tama:tama/rad-ml}{ani:ani/tad-loc}")));
        addCase(AnalyzerCase("tamajja", arrayOf("{tamajja:tamajja/ad-ml}")));
        addCase(AnalyzerCase("tamakkua", arrayOf("{tamakkua:tamakkua/pd-ml-p}")));
        addCase(AnalyzerCase("tamakkunani", arrayOf("{tamakku:tamakku/rpd-ml-p}{nani:nani/tpd-loc-p}")));
        addCase(AnalyzerCase("tamakkunanngat", arrayOf("{tamakku:tamakku/rpd-ml-p}{nanngat:nanngat/tpd-abl-p}")));
        addCase(AnalyzerCase("tamakkuninga", arrayOf("{tamakku:tamakku/rpd-ml-p}{ninga:ninga/tpd-acc-p}")));
        addCase(AnalyzerCase("tamakkuninnga", arrayOf("{tamakku:tamakku/rpd-ml-p}{ninnga:ninga/tpd-acc-p}"))
                .isMisspelled());
        addCase(AnalyzerCase("tamakkununga", arrayOf("{tamakku:tamakku/rpd-ml-p}{nunga:nunga/tpd-dat-p}")));
        addCase(AnalyzerCase("tamaksuminga", arrayOf("{tamaksu:tamaksu/rpd-ml-s}{minga:minga/tpd-acc-s}")));
        addCase(AnalyzerCase("tamaksumunga", arrayOf("{tamaksu:tamaksu/rpd-ml-s}{munga:munga/tpd-dat-s}")));


        addCase(AnalyzerCase("tamani", arrayOf("{tam:tama/rad-ml}{ani:ani/tad-loc}"))
                .isMisspelled()
                .comment("Should be 'tamaani'"));
        addCase(AnalyzerCase("tamanna", arrayOf("{tamanna:tamanna/pd-ml-s}")));
        addCase(AnalyzerCase("tamannali", arrayOf("{tamanna:tamanna/pd-ml-s}{li:li/1q}")));
        addCase(AnalyzerCase("tamannalu", arrayOf("{tamanna:tamanna/pd-ml-s}{lu:lu/1q}")));
        addCase(AnalyzerCase("tamarmik", arrayOf("{tamarmik:tamarmik/1p}")));
        addCase(AnalyzerCase("tamassuminga", arrayOf("{tamassu:tamaksu/rpd-ml-s}{minga:minga/tpd-acc-s}")));
        addCase(AnalyzerCase("tamassumunga", arrayOf("{tamassu:tamaksu/rpd-ml-s}{munga:munga/tpd-dat-s}")));
        addCase(AnalyzerCase("tamatuma", arrayOf("{tamatu:tamatu/rpd-ml-s}{ma:ma/tpd-gen-s}")));
        addCase(AnalyzerCase("tamatumani", arrayOf("{tamatu:tamatu/rpd-ml-s}{mani:mani/tpd-loc-s}")));
        addCase(AnalyzerCase("tamatuminga", arrayOf("{tamatu:tamatu/rpd-ml-s}{minga:minga/tpd-acc-s}")));
        addCase(AnalyzerCase("tamatuminnga", arrayOf("{tamatu:tamatu/rpd-ml-s}{minnga:minga/tpd-acc-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("tamatumunga", arrayOf("{tamatu:tamatu/rpd-ml-s}{munga:munga/tpd-dat-s}")));
        addCase(AnalyzerCase("tamatumunnga", arrayOf("{tamatu:tamatu/rpd-ml-s}{munnga:munga/tpd-dat-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("tamaunga", arrayOf("{tama:tama/rad-ml}{unga:unga/tad-dat}")));
        addCase(AnalyzerCase("tanna", arrayOf("{tanna:taamna/pd-sc-s}"))
                .isMisspelled()
                .comment("Should be 'taanna'"));
        addCase(AnalyzerCase("taqqaani", arrayOf("{taqqa:taqqa/rad-sc}{ani:ani/tad-loc}")));
        addCase(AnalyzerCase("taqqakkununga", arrayOf("{taqqakku:taqqapku/rpd-mlsc-p}{nunga:nunga/tpd-dat-p}")));
        addCase(AnalyzerCase("taqqiit", arrayOf("{taqqi:taqqiq/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("tausan", arrayOf("{tausan:tausat/1n}")));
        addCase(AnalyzerCase("tavani", arrayOf("{tav:tagv/rad-sc}{ani:ani/tad-loc}"))
                .isMisspelled());
        addCase(AnalyzerCase("tavva", arrayOf("{tavva:tagva/ad-sc}")));
        addCase(AnalyzerCase("tavvani", arrayOf("{tavv:tagv/rad-sc}{ani:ani/tad-loc}")));
        addCase(AnalyzerCase("tavvanngat", arrayOf("{tavv:tagv/rad-sc}{anngat:anngat/tad-abl}")));
        addCase(AnalyzerCase("tavvunga", arrayOf("{tavv:tagv/rad-sc}{unga:unga/tad-dat}")));
        addCase(AnalyzerCase("tavvuuna", arrayOf("{tavv:tagv/rad-sc}{uuna:uuna/tad-via}")));
        addCase(AnalyzerCase("tigusinirmut", arrayOf("{tigu:tigu/1v}{si:si/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("tikirarjuaq", arrayOf("{tikirarjuaq:tikirarjuaq/1n}")));
        addCase(AnalyzerCase("tikittugu", arrayOf("{tikit:tikit/1v}{tugu:lugu/tv-part-1s-3s-prespas}")));
        addCase(AnalyzerCase("timimigut", arrayOf("{timi:timi/1n}{migut:migut/tn-via-s-3s}")));
        addCase(AnalyzerCase("timiujuq", arrayOf("{timi:timi/1n}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("timiujut", arrayOf("{timi:timi/1n}{u:u/1nv}{jut:jut/tv-ger-3p}")));

        // 2020-04, BF:
//		titiqqa... :	toute la série de mots commençant par « titiqqa... »
//		titiqqat : « document », « petition », « letter » --- quelque chose qui est écrit
//
//		J’ai lu récemment que « qqat » serait une forme plurielle équivalente à « rait » (raq + it).
//		Je n’ai jamais su comment traiter ça auparavant. J’aurais peut-être maintenant une solution, mais je dois étudier ça davantage.
//		De là le correctDecompUnknown().

        addCase(AnalyzerCase("titiqqak", null)
                .correctDecompUnknown());
        addCase(AnalyzerCase("titiqqakkut", null)
                .correctDecompUnknown()
                .comment("See comment for 'titiqqak'"));
        addCase(AnalyzerCase("titiqqakkuvik", null)
                .correctDecompUnknown()
                .comment("See comment for 'titiqqak'"));
        addCase(AnalyzerCase("titiqqamik", null)
                .correctDecompUnknown()
                .comment("See comment for 'titiqqak'"));
        addCase(AnalyzerCase("titiqqanik", arrayOf("[decomposition:/titiqqa(titiqqaq)/nik(nik)/]"))
                .correctDecompUnknown()
                .comment("See comment for 'titiqqak'"));
        addCase(AnalyzerCase("titiqqanit", arrayOf("[decomposition:/titiqqa(titiqqaq)/nit(nit)/]"))
                .correctDecompUnknown()
                .comment("See comment for 'titiqqak'"));
        addCase(AnalyzerCase("titiqqaq", arrayOf("[decomposition:/titiqqaq(titiqqaq)/]"))
                .correctDecompUnknown()
                .comment("See comment for 'titiqqak'"));
        addCase(AnalyzerCase("titiqqat", arrayOf("[decomposition:/titiqqat(titiqqat)/]"))
                .correctDecompUnknown()
                .comment("See comment for 'titiqqak'"));
        addCase(AnalyzerCase("titiqqatigut", arrayOf("[decomposition:/titiqqa(titiqqaq)/tigut(tigut)/]"))
                .correctDecompUnknown()
                .comment("See comment for 'titiqqak'"));
        addCase(AnalyzerCase("titiraqsimajunik", arrayOf("{titi:titiq/1v}{raq:raq/1vv}{sima:sima/1vv}{ju:juq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("titiraqsimajunut", arrayOf("{titi:titiq/1v}{raq:raq/1vv}{sima:sima/1vv}{ju:juq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("titiraqsimajut", arrayOf("{titi:titiq/1v}{raq:raq/1vv}{sima:sima/1vv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("titiraqsimaningit", arrayOf("{titi:titiq/1v}{raq:raq/1vv}{sima:sima/1vv}{ni:niq/2vn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("titiraqti", arrayOf("{titi:titiq/1v}{raq:raq/1vv}{ti:ji/1vn}")));
        addCase(AnalyzerCase("titiraqtii", arrayOf("{titi:titiq/1v}{raq:raq/1vv}{ti:ji/1vn}{i:it/tn-nom-p}")));
        addCase(AnalyzerCase("tukiliuqtausimajuq", arrayOf("{tuki:tuki/1n}{liuq:liuq/1nv}{ta:jaq/1vn}{u:u/1nv}{sima:sima/1vv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("tukisinaliqtissimajuq", arrayOf("{tukisi:tukisi/1v}{na:naq/1vv}{liq:liq/1vv}{tis:tit/1vv}{sima:sima/1vv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("tuksiarniq", arrayOf("{tuksiar:tuksiaq/1v}{niq:niq/2vn}")));
        addCase(AnalyzerCase("tullia", arrayOf("{tulli:tugli/1n}{a:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("tungaagut", arrayOf("{tunga:tunga/1n}{agut:ngagut/tn-via-s-4s}")));
        addCase(AnalyzerCase("tungilia", arrayOf("{tungili:tungiliq/1n}{a:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("tungilinga", arrayOf("{tungili:tungiliq/1n}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("tungilira", arrayOf("{tungili:tungiliq/1n}{ra:ga/tn-nom-s-1s}")));
        addCase(AnalyzerCase("tunngasugit", arrayOf("{tunnga:tunnga/1v}{su:suk/1vv}{git:git/tv-imp-2s}")));
        addCase(AnalyzerCase("tunngasugitsi", arrayOf("{tunnga:tunnga/1v}{su:suk/1vv}{gitsi:gipsi/tv-imp-2p}")));
        addCase(AnalyzerCase("tunngavikkut", arrayOf("{tunnga:tunnga/1v}{vi:vik/3vn}{kkut:kkut/1nn}"))
                .comment("Tunngavik Nunavut Inc."));
        addCase(AnalyzerCase("tununiq", arrayOf("{tununiq:tununiq/1n}")));
        addCase(AnalyzerCase("tupiq", arrayOf("{tupiq:tupiq/1n}")));
        addCase(AnalyzerCase("turaangajunik", arrayOf("{turaa:turaaq/1v}{nga:nga/1vv}{ju:juq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("turaangajunit", arrayOf("{turaa:turaaq/1v}{nga:nga/1vv}{ju:juq/1vn}{nit:nit/tn-abl-p}")));
        addCase(AnalyzerCase("turaangajuq", arrayOf("{turaa:turaaq/1v}{nga:nga/1vv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("turaangajut", arrayOf("{turaa:turaaq/1v}{nga:nga/1vv}{jut:jut/tv-ger-3p}")));
        addCase(AnalyzerCase("tusaajaqtuqsimajunik", arrayOf("{tusaa:tusaa/1v}{jaqtuq:jaqtuq/1vv}{sima:sima/1vv}{ju:juq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("tusaaji", arrayOf("{tusaaji:tusaaji/1n}")));
        addCase(AnalyzerCase("tusaajikkut", arrayOf("{tusaaji:tusaaji/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("tusaajitigu", arrayOf("{tusaaji:tusaaji/1n}{tigu:tigut/tn-via-p}")));
        addCase(AnalyzerCase("tusaajitigut", arrayOf("{tusaaji:tusaaji/1n}{tigut:tigut/tn-via-p}")));
        addCase(AnalyzerCase("tusaajitiguuqtuq", arrayOf("{tusaaji:tusaaji/1n}{tigu:tigut/tn-via-p}{uq:uq/1nv}{tuq:juq/1vn}")));
        addCase(AnalyzerCase("tusaajitiguurunniiqtuq", arrayOf("{tusaaji:tusaaji/1n}{tigu:tigut/tn-via-p}{u:uq/1nv}{runniiq:junniiq/1vv}{tuq:juq/1vn}")));
        addCase(AnalyzerCase("tusaajititigu", arrayOf("{tusaaji:tusaaji/1n}{titigut:tigut/tn-via-p}"))
                .isMisspelled());
        addCase(AnalyzerCase("tusarumatuinnaqtunga", arrayOf("{tusa:tusaq/1v}{ruma:juma/1vv}{tuinnaq:tuinnaq/1vv}{tunga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("tutu", arrayOf("[decomposition:/tutu(tutu)/]"))
                .isProperName());
        addCase(AnalyzerCase("tutuu", arrayOf("[decomposition:/tutuu(tutuu)/]"))
                .isProperName());
        addCase(AnalyzerCase("tuutu", arrayOf("[decomposition:/tuutu(tuutu)/]"))
                .isProperName());
        addCase(AnalyzerCase("tuutuu", arrayOf("[decomposition:/tuutuu(tuutuu)/]"))
                .isProperName());
        addCase(AnalyzerCase("puraian", null)
                .isProperName()
                .comment("Brian"));
        addCase(AnalyzerCase("vuraian", null)
                .isProperName()
                .comment("Brian"));
        addCase(AnalyzerCase("vuraijan", null)
                .isProperName()
                .comment("Brian"));
        addCase(AnalyzerCase("uannangani", arrayOf("{uanna:uangnaq/1n}{ngani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("uattiaq", arrayOf("{uattiaq:uattiaq/1a}")));
        addCase(AnalyzerCase("uattiaru", arrayOf("{uattiaru:uattiaruk/1a}")));
        addCase(AnalyzerCase("uqaalautaa", arrayOf("{uqa:uqaq/1v}{ala:allak/1vv}{uta:ut/1vn}{a:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("uiliams", arrayOf("[decomposition:/uiliams(uiliams)/]"))
                .isProperName());
        addCase(AnalyzerCase("ukaliq", arrayOf("[decomposition:/ukaliq(ukaliq)/]"))
                .isProperName());
        addCase(AnalyzerCase("ukiukkut", arrayOf("{ukiu:ukiuq/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("ukiunik", arrayOf("{ukiu:ukiuq/1n}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("ukiunut", arrayOf("{ukiu:ukiuq/1n}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("ukiuqtaqtumi", arrayOf("{ukiuqtaqtu:ukiuqtaqtuq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("ukiurtartumi", arrayOf("{ukiurtartu:ukiuqtaqtuq/1n}{mi:mi/tn-loc-s}"))
                .isMisspelled());
        addCase(AnalyzerCase("ukua", arrayOf("{ukua:ukua/pd-sc-p}")));
        addCase(AnalyzerCase("ukunani", arrayOf("{uku:uku/1v}{nani:nani/tv-part-3s}")));
        addCase(AnalyzerCase("ukuninga", arrayOf("{uku:uku/rpd-sc-p}{ninga:ninga/tpd-acc-p}")));
        addCase(AnalyzerCase("ukununga", arrayOf("{uku:uku/rpd-sc-p}{nunga:nunga/tpd-dat-p}")));
        addCase(AnalyzerCase("ulaaju", arrayOf("[decomposition:/ulaaju(ulaaju)/]"))
                .isProperName());
        addCase(AnalyzerCase("ulaajuk", arrayOf("[decomposition:/ulaajuk(ulaajuk)/]"))
                .isProperName());
        addCase(AnalyzerCase("ulaajuq", arrayOf("[decomposition:/ulaajuq(ulaajuq)/]"))
                .isProperName());
        addCase(AnalyzerCase("ullaakkut", arrayOf("{ullaa:ublaaq/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("ullaaq", arrayOf("{ullaaq:ublaaq/1n}")));
        addCase(AnalyzerCase("ulluit", arrayOf("{ullu:ubluq/1n}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("ullumi", arrayOf("{ullumi:ullumi/1a}")));
        addCase(AnalyzerCase("ullumimut", arrayOf("{ullumi:ullumi/1a}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("ullumiujuq", arrayOf("{ullumi:ullumi/1a}{u:u/1nv}{juq:juq/1vn}")));
        addCase(AnalyzerCase("ulluq", arrayOf("{ulluq:ubluq/1n}")));
        addCase(AnalyzerCase("una", arrayOf("{una:una/pd-sc-s}")));
        addCase(AnalyzerCase("ungataani", arrayOf("{ungata:ungata/1n}{ani:ngani/tn-loc-s-4s}")));
        addCase(AnalyzerCase("ungataanut", arrayOf("{ungata:ungata/1n}{anut:nganut/tn-dat-s-4s}")));
        addCase(AnalyzerCase("unikkaangat", arrayOf("{unikkaa:unipkaaq/1n}{ngat:ngat/tn-nom-s-4p}")));
        addCase(AnalyzerCase("unikkaangit", arrayOf("{unikkaa:unipkaaq/1n}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("unikkaaq", arrayOf("{unikkaaq:unipkaaq/1n}")));
        addCase(AnalyzerCase("unikkaat", arrayOf("{unikkaa:unipkaaq/1n}{t:it/tn-nom-p}")));
        addCase(AnalyzerCase("unnuk", arrayOf("{unnuk:unnuk/1n}")));
        addCase(AnalyzerCase("unnukkut", arrayOf("{unnu:unnuk/1n}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("unnusakkut", arrayOf("{unnusa:unnuksaq/1n}{kkut:kkut/tn-via-s}")));
        addCase(AnalyzerCase("unnusami", arrayOf("{unnusa:unnuksaq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("upinnarani", arrayOf("{upinnarani:upinnarani/1a}")));
        addCase(AnalyzerCase("uqalaurama", arrayOf("{uqa:uqaq/1v}{lau:lauq/1vv}{rama:gama/tv-caus-1s}")));
        addCase(AnalyzerCase("uqalaurmat", arrayOf("{uqa:uqaq/1v}{laur:lauq/1vv}{mat:mat/tv-caus-4s}")));
        addCase(AnalyzerCase("uqalimaaqtauninga", arrayOf("{uqa:uqaq/1v}{limaaq:limaaq/2vv}{ta:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("uqalimaaqtauninginnut", arrayOf("{uqa:uqaq/1v}{limaaq:limaaq/2vv}{ta:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{nginnut:nginnut/tn-dat-p-4s}")));
        addCase(AnalyzerCase("uqalimaaqtauningit", arrayOf("{uqa:uqaq/1v}{limaaq:limaaq/2vv}{ta:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("uqalimaaqtauqullugu", arrayOf("{uqa:uqaq/1v}{limaaq:limaaq/2vv}{ta:jaq/1vn}{u:u/1nv}{qu:qu/2vv}{llugu:lugu/tv-part-1s-3s-prespas}")));
        addCase(AnalyzerCase("uqalimaarniq", arrayOf("{uqa:uqaq/1v}{limaar:limaaq/2vv}{niq:niq/2vn}")));
        addCase(AnalyzerCase("uqalimaarvik", arrayOf("{uqa:uqaq/1v}{limaar:limaaq/2vv}{vik:vik/3vn}")));
        addCase(AnalyzerCase("uqaqqaugama", arrayOf("{uqa:uqaq/1v}{qqau:qqau/1vv}{gama:gama/tv-caus-1s}")));
        addCase(AnalyzerCase("uqaqqaugamailaak", arrayOf("{uqa:uqaq/1v}{qqau:qqau/1vv}{gama:gama/tv-caus-1s}{ilaak:ilaak/1q}")));
        addCase(AnalyzerCase("uqaqqaugavit", arrayOf("{uqa:uqaq/1v}{qqau:qqau/1vv}{gavit:gavit/tv-caus-2s}")));
        addCase(AnalyzerCase("uqaqqaummat", arrayOf("{uqa:uqaq/1v}{qqau:qqau/1vv}{mmat:mat/tv-caus-4s}")));
        addCase(AnalyzerCase("uqaqsimammat", arrayOf("{uqaq:uqaq/1v}{sima:sima/1vv}{mmat:mat/tv-caus-4s}")));
        addCase(AnalyzerCase("uqaqti", arrayOf("{uqaq:uqaq/1v}{ti:ji/1vn}")));
        addCase(AnalyzerCase("uqaqtii", arrayOf("{uqaq:uqaq/1v}{ti:ji/1vn}{i:k/tn-nom-d}")));
        addCase(AnalyzerCase("uqaqtiup", arrayOf("{uqaq:uqaq/1v}{ti:ji/1vn}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("uqartii", arrayOf("{uqar:uqaq/1v}{ti:ji/1vn}{i:it/tn-nom-p}")));
        addCase(AnalyzerCase("uqarumajunga", arrayOf("{uqa:uqaq/1v}{ruma:juma/1vv}{junga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("uqarumavunga", arrayOf("{uqa:uqaq/1v}{ruma:juma/1vv}{vunga:vunga/tv-dec-1s}")));
        addCase(AnalyzerCase("uqarunnaqtunga", arrayOf("{uqa:uqaq/1v}{runnaq:junnaq/1vv}{tunga:junga/tv-ger-1s}")));
        addCase(AnalyzerCase("uqausiit", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("uqausikkut", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("uqausiksait", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("uqausiksangit", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{ksa:ksaq/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("uqausiksat", arrayOf("{uqausi:uqausiq/1n}{ksa:ksaq/1nn}{t:it/tn-nom-p}"))
                .possiblyMisspelledWord()
                .comment("TODO-BF: Please add a SHORT comment; "));
        addCase(AnalyzerCase("uqausilirinirmut", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("uqausinga", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("uqausingit", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("uqausiq", arrayOf("{uqa:uqaq/1v}{usiq:usiq/1vn}")));
        addCase(AnalyzerCase("uqausiqtigut", arrayOf("{uqa:uqaq/1v}{usiq:usiq/1vn}{tigut:tigut/tn-via-p}")));
        addCase(AnalyzerCase("uqausirijanga", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{ri:gi/1nv}{ja:jaq/1vn}{nga:nga/tn-nom-s-4s}")));
        addCase(AnalyzerCase("uqausirmik", arrayOf("{uqa:uqaq/1v}{usir:usiq/1vn}{mik:mik/tn-acc-s}")));
        addCase(AnalyzerCase("uqausirnik", arrayOf("{uqa:uqaq/1v}{usir:usiq/1vn}{nik:nik/tn-acc-p}")));
        addCase(AnalyzerCase("uqausirnut", arrayOf("{uqa:uqaq/1v}{usir:usiq/1vn}{nut:nut/tn-dat-p}")));
        addCase(AnalyzerCase("uqausissangit", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{ssa:ksaq/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("uqausitsangit", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{tsa:ksaq/1nn}{ngit:ngit/tn-nom-p-4s}")));
        addCase(AnalyzerCase("uqausituinnakkut", arrayOf("{uqa:uqaq/1v}{usi:usiq/1vn}{tuinna:tuinnaq/2nn}{kkut:kkut/1nn}")));
        addCase(AnalyzerCase("uqqurmiut", arrayOf("{uqqurmiut:uqqurmiut/1n}")));
        addCase(AnalyzerCase("uqsualuit", arrayOf("{uqsu:uqsuq/1n}{alu:aluk/1nn}{it:it/tn-nom-p}")));
        addCase(AnalyzerCase("uqsualuk", arrayOf("{uqsu:uqsuq/1n}{aluk:aluk/1nn}")));
        addCase(AnalyzerCase("uqsualummut", arrayOf("{uqsu:uqsuq/1n}{alum:aluk/1nn}{mut:mut/tn-dat-s}")));
        addCase(AnalyzerCase("uqsualuup", arrayOf("{uqsu:uqsuq/1n}{alu:aluk/1nn}{up:up/tn-gen-s}")));
        addCase(AnalyzerCase("uqsuqtuumi", arrayOf("{uqsuqtuu:uqsuqtuuq/1n}{mi:mi/tn-loc-s}")));
        addCase(AnalyzerCase("utirluta", arrayOf("{utir:utiq/1v}{luta:luta/tv-part-1p-fut}")));
        addCase(AnalyzerCase("utupiri", arrayOf("{utupiri:utupiri/1n}")));
        addCase(AnalyzerCase("uuktutigilugu", arrayOf("{uuk:uuk/1v}{tu:tuq/1vv}{ti:ut/1vn}{gi:gi/1nv}{lugu:lugu/tv-part-1s-3s-fut}"))
                .isMisspelled());
        addCase(AnalyzerCase("uuktuutigilugu", arrayOf("{uuk:uuk/1v}{tu:tuq/1vv}{uti:ut/1vn}{gi:gi/1nv}{lugu:lugu/tv-part-1s-3s-fut}")));
        addCase(AnalyzerCase("uupraijan", null)
                .isProperName()
                .comment("O'brien"));
        addCase(AnalyzerCase("uuttuutigillugu", arrayOf("{uut:uuk/1v}{tu:tuq/1vv}{uti:ut/1vn}{gi:gi/1nv}{llugu:lugu/tv-part-1s-3s-prespas}")));
        addCase(AnalyzerCase("uuttuutigilugu", arrayOf("{uut:uuk/1v}{tu:tuq/1vv}{uti:ut/1vn}{gi:gi/1nv}{lugu:lugu/tv-part-1s-3s-fut}")));
        addCase(AnalyzerCase("uuviti", null)
                .isProperName()
                .comment("Ovide"));
        addCase(AnalyzerCase("uuvuraian", null)
                .isProperName()
                .comment("O'brien"));
        addCase(AnalyzerCase("uuvuraijan", null)
                .isProperName()
                .comment("O'brien"));
        addCase(AnalyzerCase("uvagut", arrayOf("{uvagut:uvagut/1p}")));
        addCase(AnalyzerCase("uvalu", arrayOf("{uvalu:uvvalu/1c}"))
                .isMisspelled());
        addCase(AnalyzerCase("uvanga", arrayOf("{uvanga:uvanga/1p}")));
        addCase(AnalyzerCase("uvani", arrayOf("{uv:uv/rad-sc}{ani:ani/tad-loc}")));
        addCase(AnalyzerCase("uvannik", arrayOf("{uva:uva/1rpr}{nnik:mnik/tn-acc-s-1s}")));
        addCase(AnalyzerCase("uvannut", arrayOf("{uva:uva/1rpr}{nnut:mnut/tn-dat-s-1s}")));
        addCase(AnalyzerCase("uvattinni", arrayOf("{uva:uva/1rpr}{ttinni:ptingni/tn-loc-p-1p}")));
        addCase(AnalyzerCase("uvattinnik", arrayOf("{uva:uva/1rpr}{ttinnik:ptingnik/tn-acc-p-1p}")));
        addCase(AnalyzerCase("uvattinnut", arrayOf("{uva:uva/1rpr}{ttinnut:ptingnut/tn-dat-p-1p}")));
        addCase(AnalyzerCase("uvunga", arrayOf("{uv:uv/rad-sc}{unga:unga/tad-dat}")));
        addCase(AnalyzerCase("uvva", arrayOf("{uvva:ubva/ad-sc}")));
        addCase(AnalyzerCase("uvvalu", arrayOf("{uvvalu:uvvalu/1c}")));
        addCase(AnalyzerCase("uvvalukiaq", arrayOf("{uvvalu:uvvalu/1c}{kiaq:kia/1q}")));
        addCase(AnalyzerCase("uvvaluunni", arrayOf("{uvvaluunni:uvvaluunniit/1c}"))
                .isMisspelled());
        addCase(AnalyzerCase("uvvaluunniit", arrayOf("{uvvaluunniit:uvvaluunniit/1c}")));
        addCase(AnalyzerCase("vaanavas", null)
                .isProperName()
                .comment("Barnabas"));
        addCase(AnalyzerCase("viliams", arrayOf("[decomposition:/viliams(viliams)/]"))
                .isProperName());
        addCase(AnalyzerCase("viuris", null)
                .isProperName()
                .comment("Ferris"));
        addCase(AnalyzerCase("vivvuari", arrayOf("{vivvuari:vivvuari/1n}")));
    }
}
