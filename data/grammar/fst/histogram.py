"""
Milestone 4 of doc/dev/plans/fst-analyzer-plan.md: runs hfst-lookup over a small
curated batch of gold-standard words and reports a histogram in the same
shape as the existing 919-word one (cli/src/test/kotlin/org/iutools/morph/
MorphAnalGoldStandard_Hansard.kt's own regression suite) -- first-
decomposition-correct / correct-but-not-first / correct-not-present /
no-decomps.

This is also the "real, working answer instead of eyeballing results" the
plan calls for on the output-format question: HFST's own analysis string
(e.g. "amit+1v++juq+1vn") is converted here into a list of (canonical, id)
pairs and compared against the same pairs parsed out of Benoit's own
{surface:canonical/id} notation.

Deliberate scope limit: the comparison uses (canonical, id) pairs only, not
full {surface:canonical/id} triples -- it does not attempt to recover which
substring of the input word each morpheme matched. That's a real, separate
problem (HFST's optimized-lookup only exposes whole-string input/output
pairs, not an internal per-morpheme alignment) that doesn't need solving
for *this* comparison: the surface word is already known correct by
construction (it's literally the string fed to hfst-lookup, taken from the
gold standard), so checking that the analyser's own canonical+id sequence
matches the gold canonical+id sequence is sufficient to confirm a correct
decomposition, without also reconstructing surface spans per morpheme.

The 8 words here are hardcoded, copied verbatim from
MorphAnalGoldStandard_Hansard.kt (never retyped by hand -- see AGENTS.md's
"Preserving data integrity") rather than read from that Kotlin file
directly, since :cli isn't built as part of this dev-time tool.

Usage (from data/grammar/fst/, after building lexicon-analyser.hfstol per
phonology.xfscript's header):
    python3 histogram.py
"""
import re
import subprocess
from pathlib import Path

ANALYSER = Path(__file__).parent / "lexicon-analyser.hfstol"

# (surface word, [gold decomposition strings]) -- copied verbatim from
# MorphAnalGoldStandard_Hansard.kt; more than one string means more than
# one gold-accepted parse (e.g. "anginngittut"'s root is ambiguous between
# "angi" and "angiq" -- our minimal lexicon only has "angi", so it should
# match the first alternative).
GOLD_CASES = [
    ("akinga", ["{aki:aki/1n}{nga:nga/tn-nom-s-4s}"]),
    ("amittuq", ["{amit:amit/1v}{tuq:juq/1vn}"]),
    ("aanniaqtulirijikkunnut", [
        "{aanniaq:aanniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}"
        "{kkun:kkut/1nn}{nut:nut/tn-dat-p}",
    ]),
    ("ammattauq", ["{amma:amma/1c}{ttauq:ttauq/1q}"]),
    ("ilaaqai", ["{ilaa:ilaak/1e}{qai:qai/1q}"]),
    ("maanna", ["{maanna:maanna/1a}"]),
    ("kina", ["{kina:kina/1p}"]),
    ("anginngittut", [
        "{angi:angi/1v}{nngit:nngit/1vv}{tut:jut/tv-ger-3p}",
        "{angi:angiq/1v}{nngit:nngit/1vv}{tut:jut/tv-ger-3p}",
    ]),
    ("arnait", ["{arna:arnaq/1n}{it:it/tn-nom-p}"]),
    ("igluit", ["{iglu:iglu/1n}{it:it/tn-nom-p}"]),
    ("ministaujuq", ["{minista:minista/1n}{u:u/1nv}{juq:juq/1vn}"]),
    ("piliriangujuq", ["{piliria:piliriaq/1n}{ngu:u/1nv}{juq:juq/1vn}"]),
    ("makpigaq", ["{makpi:makpiq/1v}{gaq:gaq/1vn}"]),
    ("qimirruniq", ["{qimirru:qimirru/1v}{niq:niq/2vn}"]),
    ("ilinniarniq", ["{ilinniar:ilinniaq/1v}{niq:niq/2vn}"]),
    ("arraagumut", ["{arraagu:arraagu/1n}{mut:mut/tn-dat-s}"]),
    ("arraagunik", ["{arraagu:arraagu/1n}{nik:nik/tn-acc-p}"]),
    ("akingit", ["{aki:aki/1n}{ngit:ngit/tn-nom-p-4s}"]),
    ("atingit", ["{ati:atiq/1n}{ngit:ngit/tn-nom-p-4s}"]),
    ("arraagumi", ["{arraagu:arraagu/1n}{mi:mi/tn-loc-s}"]),
    ("nanulik", ["{nanu:nanuq/1n}{lik:lik/1nn}"]),
    ("arraagunit", ["{arraagu:arraagu/1n}{nit:nit/tn-abl-p}"]),
    ("arraaguup", ["{arraagu:arraagu/1n}{up:up/tn-gen-s}"]),
    ("amisuni", ["{amisu:amisu/1n}{ni:ni/tn-loc-p}"]),
    ("ministamik", ["{minista:minista/1n}{mik:mik/tn-acc-s}"]),
    ("akinginnut", ["{aki:aki/1n}{nginnut:nginnut/tn-dat-p-4s}"]),
    ("akiraqtuqtut", ["{akiraq:akiraq/1v}{tuq:tuq/1vv}{tut:jut/tv-ger-3p}"]),
    ("quviattunga", ["{quviat:quviak/1v}{tunga:junga/tv-ger-1s}"]),
    ("ammalu", ["{amma:amma/1c}{lu:lu/1q}"]),
    ("nikuvippunga", ["{nikuvip:nikuvik/1v}{punga:vunga/tv-dec-1s}"]),
    ("gavamatuqakkut", ["{gavama:gavama/1n}{tuqa:tuqaq/1nn}{kkut:kkut/1nn}"]),
    ("titiraqsimajut", ["{titi:titiq/1v}{raq:raq/1vv}{sima:sima/1vv}{jut:jut/tv-ger-3p}"]),
    ("isumagillugu", ["{isuma:isuma/1n}{gi:gi/1nv}{llugu:lugu/tv-part-1s-3s-prespas}"]),
    ("tusaajitigut", ["{tusaaji:tusaaji/1n}{tigut:tigut/tn-via-p}"]),
    ("kisianili", ["{kisiani:kisiani/1c}{li:li/1q}"]),
    ("katillugit", ["{kati:kati/1v}{llugit:lugit/tv-part-1s-3p-prespas}"]),
    ("uqalimaarniq", ["{uqa:uqaq/1v}{limaar:limaaq/2vv}{niq:niq/2vn}"]),
    ("asinginnit", ["{asi:asi/1n}{nginnit:nginnit/tn-abl-p-4s}"]),
    ("kikkutuinnait", ["{kikku:kikkut/1p}{tuinna:tuinnaq/2nn}{it:it/tn-nom-p}"]),
    ("asinginni", ["{asi:asi/1n}{nginni:nginni/tn-loc-p-4s}"]),
    ("ajjigiinngittunik", ["{ajji:ajji/1n}{gii:giik/2nv}{nngit:nngit/1vv}{tu:juq/1vn}{nik:nik/tn-acc-p}"]),
    ("turaangajuq", ["{turaa:turaaq/1v}{nga:nga/1vv}{juq:juq/1vn}"]),
    ("arraagutamaat", ["{arraagu:arraagu/1n}{tamaa:tamaaq/1nn}{t:it/tn-nom-p}"]),
    ("ilangat", ["{ila:ila/1n}{ngat:ngat/tn-nom-s-4p}"]),
    ("niriuppugut", ["{niriup:niriuk/1v}{pugut:vugut/tv-dec-1p}"]),
    ("ilagijaujuq", ["{ila:ila/1n}{gi:gi/1nv}{ja:jaq/1vn}{u:u/1nv}{juq:juq/1vn}"]),
    ("maligaksait", ["{mali:malik/1v}{ga:gaq/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}"]),
    ("qaujimagama", ["{qaujima:qaujima/1v}{gama:gama/tv-caus-1s}"]),
    ("qaujimagatta", ["{qaujima:qaujima/1v}{gatta:gapta/tv-caus-1p}"]),
    ("qaujimagavit", ["{qaujima:qaujima/1v}{gavit:gavit/tv-caus-2s}"]),
    ("qujannamiingujutit", ["{qujannamii:qujannamiik/1a}{ngu:u/1nv}{jutit:jutit/tv-ger-2s}"]),
    ("angiqtugut", ["{angiq:angiq/1v}{tugut:jugut/tv-ger-1p}"]),
    ("maligarmut", ["{mali:malik/1v}{gar:gaq/1vn}{mut:mut/tn-dat-s}"]),
    ("nutaamut", ["{nutaa:nutaaq/1n}{mut:mut/tn-dat-s}"]),
    ("maannaujumi", ["{maanna:maanna/1a}{u:u/1nv}{ju:juq/1vn}{mi:mi/tn-loc-s}"]),
    ("maligarmi", ["{mali:malik/1v}{gar:gaq/1vn}{mi:mi/tn-loc-s}"]),
    ("nutaami", ["{nutaa:nutaaq/1n}{mi:mi/tn-loc-s}"]),
    ("maligaksanit", ["{mali:malik/1v}{ga:gaq/1vn}{ksa:ksaq/1nn}{nit:nit/tn-abl-p}"]),
    ("maligarnit", ["{mali:malik/1v}{gar:gaq/1vn}{nit:nit/tn-abl-p}"]),
    ("nutaanit", ["{nutaa:nutaaq/1n}{nit:nit/tn-abl-p}"]),
    ("avittuqsimajuni", ["{avit:avik/1v}{tuq:tuq/1vv}{sima:sima/1vv}{ju:juq/1vn}{ni:ni/tn-loc-p}"]),
    ("angijumik", ["{angi:angi/1v}{ju:juq/1vn}{mik:mik/tn-acc-s}"]),
    ("maligarmik", ["{mali:malik/1v}{gar:gaq/1vn}{mik:mik/tn-acc-s}"]),
    ("nutaamik", ["{nutaa:nutaaq/1n}{mik:mik/tn-acc-s}"]),
    ("atirnik", ["{atir:atiq/1n}{nik:nik/tn-acc-p}"]),
    ("nutaanik", ["{nutaa:nutaaq/1n}{nik:nik/tn-acc-p}"]),
    ("ajjigiinngittunut", ["{ajji:ajji/1n}{gii:giik/2nv}{nngit:nngit/1vv}{tu:juq/1vn}{nut:nut/tn-dat-p}"]),
    ("nutaanut", ["{nutaa:nutaaq/1n}{nut:nut/tn-dat-p}"]),
    ("ukiunut", ["{ukiu:ukiuq/1n}{nut:nut/tn-dat-p}"]),
    ("surusirnut", ["{surusir:surusiq/1n}{nut:nut/tn-dat-p}"]),
    ("inuttitut", ["{inut:inuk/1n}{titut:titut/tn-sim-p}"]),
    ("avittuqsimajutigut", ["{avit:avik/1v}{tuq:tuq/1vv}{sima:sima/1vv}{ju:juq/1vn}{tigut:tigut/tn-via-p}"]),
    ("ilinniarvik", ["{ilinniar:ilinniaq/1v}{vik:vik/3vn}"]),
    ("asingillu", ["{asi:asi/1n}{ngil:ngit/tn-nom-p-4s}{lu:lu/1q}"]),
    ("asinginnillu", ["{asi:asi/1n}{nginnil:nginnit/tn-abl-p-4s}{lu:lu/1q}"]),
    ("asinginnullu", ["{asi:asi/1n}{nginnul:nginnut/tn-dat-p-4s}{lu:lu/1q}"]),
    ("inulirijikkullu", ["{inu:inuk/1n}{liri:liri/1nv}{ji:ji/1vn}{kkul:kkut/1nn}{lu:lu/1q}"]),
    ("tunngasugit", ["{tunnga:tunnga/1v}{su:suk/1vv}{git:git/tv-imp-2s}"]),
    ("qanuimmat", ["{qanuim:qanuit/1v}{mat:mat/tv-caus-4s}"]),
    ("summat", ["{su:su/1v}{mmat:mat/tv-caus-4s}"]),
    ("taimaimmat", ["{taimaim:taimait/1v}{mat:mat/tv-caus-4s}"]),
    ("taimannaummat", ["{taimanna:taimanna/1a}{u:u/1nv}{mmat:mat/tv-caus-4s}"]),
    ("uqalaurmat", ["{uqa:uqaq/1v}{laur:lauq/1vv}{mat:mat/tv-caus-4s}"]),
    ("uqaqqaummat", ["{uqa:uqaq/1v}{qqau:qqau/1vv}{mmat:mat/tv-caus-4s}"]),
    ("uqaqsimammat", ["{uqaq:uqaq/1v}{sima:sima/1vv}{mmat:mat/tv-caus-4s}"]),
    ("asiagut", ["{asi:asi/1n}{agut:ngagut/tn-via-s-4s}"]),
    ("ilangagut", ["{ila:ila/1n}{ngagut:ngagut/tn-via-s-4s}"]),
    ("iluagut", ["{ilu:ilu/1n}{agut:ngagut/tn-via-s-4s}"]),
    ("kinguniagut", ["{kinguni:kinguni/1n}{agut:ngagut/tn-via-s-4s}"]),
    ("sivuniagut", ["{sivuni:sivuni/1n}{agut:ngagut/tn-via-s-4s}"]),
    ("tungaagut", ["{tunga:tunga/1n}{agut:ngagut/tn-via-s-4s}"]),
    ("akunialuk", ["{akuni:akuni/1n}{aluk:aluk/1nn}"]),
    ("uqsualuit", ["{uqsu:uqsuq/1n}{alu:aluk/1nn}{it:it/tn-nom-p}"]),
    ("uqsualuk", ["{uqsu:uqsuq/1n}{aluk:aluk/1nn}"]),
    ("uqsualummut", ["{uqsu:uqsuq/1n}{alum:aluk/1nn}{mut:mut/tn-dat-s}"]),
    ("uqsualuup", ["{uqsu:uqsuq/1n}{alu:aluk/1nn}{up:up/tn-gen-s}"]),
    ("maligaliuqti", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}"]),
    ("maligaliuqtiit", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{it:it/tn-nom-p}"]),
    ("maligaliuqtikkut", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{kkut:kkut/1nn}"]),
    ("maligaliuqtimut", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{mut:mut/tn-dat-s}"]),
    ("maligaliuqtinik", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{nik:nik/tn-acc-p}"]),
    ("maligaliuqtinit", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{nit:nit/tn-abl-p}"]),
    ("maligaliuqtinut", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{nut:nut/tn-dat-p}"]),
    ("maligaliurti", ["{mali:malik/1v}{ga:gaq/1vn}{liur:liuq/1nv}{ti:ji/1vn}"]),
    ("maligaliurtiit", ["{mali:malik/1v}{ga:gaq/1vn}{liur:liuq/1nv}{ti:ji/1vn}{it:it/tn-nom-p}"]),
    ("maligaliurtiup", ["{mali:malik/1v}{ga:gaq/1vn}{liur:liuq/1nv}{ti:ji/1vn}{up:up/tn-gen-s}"]),
    ("maligaliuqtiup", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{up:up/tn-gen-s}"]),
    ("maligaliuqtiujuq", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{u:u/1nv}{juq:juq/1vn}"]),
    ("maligaliuqtiujut", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}"]),
    ("maligaliuqtilimaanut", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{limaa:limaaq/1nn}{nut:nut/tn-dat-p}"]),
    ("maligaliuqtilimaat", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{limaa:limaaq/1nn}{t:it/tn-nom-p}"]),
    ("uqausiq", ["{uqa:uqaq/1v}{usiq:usiq/1vn}"]),
    ("uqausiqtigut", ["{uqa:uqaq/1v}{usiq:usiq/1vn}{tigut:tigut/tn-via-p}"]),
    ("uqausiit", ["{uqa:uqaq/1v}{usi:usiq/1vn}{it:it/tn-nom-p}"]),
    ("uqausiksait", ["{uqa:uqaq/1v}{usi:usiq/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}"]),
    ("uqausiksangit", ["{uqa:uqaq/1v}{usi:usiq/1vn}{ksa:ksaq/1nn}{ngit:ngit/tn-nom-p-4s}"]),
    ("uqausikkut", ["{uqa:uqaq/1v}{usi:usiq/1vn}{kkut:kkut/1nn}"]),
    ("uqausilirinirmut", ["{uqa:uqaq/1v}{usi:usiq/1vn}{liri:liri/1nv}{nir:niq/2vn}{mut:mut/tn-dat-s}"]),
    ("uqausinga", ["{uqa:uqaq/1v}{usi:usiq/1vn}{nga:nga/tn-nom-s-4s}"]),
    ("uqausingit", ["{uqa:uqaq/1v}{usi:usiq/1vn}{ngit:ngit/tn-nom-p-4s}"]),
    ("uqausirmik", ["{uqa:uqaq/1v}{usir:usiq/1vn}{mik:mik/tn-acc-s}"]),
    ("uqausirnik", ["{uqa:uqaq/1v}{usir:usiq/1vn}{nik:nik/tn-acc-p}"]),
    ("uqausirnut", ["{uqa:uqaq/1v}{usir:usiq/1vn}{nut:nut/tn-dat-p}"]),
    ("apiqqusiit", ["{apiq:apiq/1v}{qusi:usiq/1vn}{it:it/tn-nom-p}"]),
    ("apiqqusiksait", ["{apiq:apiq/1v}{qusi:usiq/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}"]),
    ("apiqqusirnut", ["{apiq:apiq/1v}{qusir:usiq/1vn}{nut:nut/tn-dat-p}"]),
    ("apiqqut", ["{apiq:apiq/1v}{qut:ut/1vn}"]),
    ("apiqquti", ["{apiq:apiq/1v}{quti:ut/1vn}"]),
    ("apiqqutik", ["{apiq:apiq/1v}{qutik:ut/1vn}"]),
    ("apiqqutiit", ["{apiq:apiq/1v}{quti:ut/1vn}{it:it/tn-nom-p}"]),
    ("apiqqutiksait", ["{apiq:apiq/1v}{quti:ut/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}"]),
    ("apiqqutinga", ["{apiq:apiq/1v}{quti:ut/1vn}{nga:nga/tn-nom-s-4s}"]),
    ("apiqqutinut", ["{apiq:apiq/1v}{quti:ut/1vn}{nut:nut/tn-dat-p}"]),
    ("ilagiarut", ["{ila:ila/1v}{gia:giaq/1vv}{rut:ut/1vn}"]),
    ("ilagiaruti", ["{ila:ila/1v}{gia:giaq/1vv}{ruti:ut/1vn}"]),
    ("ilagiarutiit", ["{ila:ila/1v}{gia:giaq/1vv}{ruti:ut/1vn}{it:it/tn-nom-p}"]),
    ("iksivauta", ["{iksiva:iksiva/1v}{uta:ut/1vn}"]),
    ("iksivautaq", ["{iksiva:iksiva/1v}{utaq:ut/1vn}"]),
    ("iksivautaup", ["{iksiva:iksiva/1v}{uta:ut/1vn}{up:up/tn-gen-s}"]),
    ("issivauta", ["{issiva:iksiva/1v}{uta:ut/1vn}"]),
    ("issivautaq", ["{issiva:iksiva/1v}{utaq:ut/1vn}"]),
    ("itsivauta", ["{itsiva:iksiva/1v}{uta:ut/1vn}"]),
    ("itsivautaq", ["{itsiva:iksiva/1v}{utaq:ut/1vn}"]),
    ("asiani", ["{asi:asi/1n}{ani:ngani/tn-loc-s-4s}"]),
    ("ataani", ["{ata:ata/1n}{ani:ngani/tn-loc-s-4s}"]),
    ("ilangani", ["{ila:ila/1n}{ngani:ngani/tn-loc-s-4s}"]),
    ("iluani", ["{ilu:ilu/1n}{ani:ngani/tn-loc-s-4s}"]),
    ("qitingani", ["{qiti:qitiq/1n}{ngani:ngani/tn-loc-s-4s}"]),
    ("saniani", ["{sani:sani/1n}{ani:ngani/tn-loc-s-4s}"]),
    ("silataani", ["{silata:silata/1n}{ani:ngani/tn-loc-s-4s}"]),
    ("sivuniani", ["{sivuni:sivuni/1n}{ani:ngani/tn-loc-s-4s}"]),
    ("sivuningani", ["{sivuni:sivuni/1n}{ngani:ngani/tn-loc-s-4s}"]),
    ("ungataani", ["{ungata:ungata/1n}{ani:ngani/tn-loc-s-4s}"]),
    ("apiqqutinganut", ["{apiq:apiq/1v}{quti:ut/1vn}{nganut:nganut/tn-dat-s-4s}"]),
    ("asianut", ["{asi:asi/1n}{anut:nganut/tn-dat-s-4s}"]),
    ("gavamanganut", ["{gavama:gavama/1n}{nganut:nganut/tn-dat-s-4s}"]),
    ("miksaanut", ["{miksa:miksa/1n}{anut:nganut/tn-dat-s-4s}"]),
    ("missaanut", ["{missa:miksa/1n}{anut:nganut/tn-dat-s-4s}"]),
    ("mitsaanut", ["{mitsa:miksa/1n}{anut:nganut/tn-dat-s-4s}"]),
    ("taimainninganut", ["{taimain:taimait/1v}{ni:niq/2vn}{nganut:nganut/tn-dat-s-4s}"]),
    ("ungataanut", ["{ungata:ungata/1n}{anut:nganut/tn-dat-s-4s}"]),
    ("katimaji", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}"]),
    ("katimajiit", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{it:it/tn-nom-p}"]),
    ("katimajinginnit", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{nginnit:nginnit/tn-abl-p-4s}"]),
    ("katimajinginnut", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{nginnut:nginnut/tn-dat-p-4s}"]),
    ("katimajingit", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ngit:ngit/tn-nom-p-4s}"]),
    ("katimajinut", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{nut:nut/tn-dat-p}"]),
    ("katimajiujut", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}"]),
    ("katimaniq", ["{kati:kati/1v}{ma:ma/1vv}{niq:niq/2vn}"]),
    ("katimautinik", ["{kati:kati/1v}{ma:ma/1vv}{uti:ut/1vn}{nik:nik/tn-acc-p}"]),
    ("katimavimmi", ["{kati:kati/1v}{ma:ma/1vv}{vim:vik/3vn}{mi:mi/tn-loc-s}"]),
    ("katimajjutiksait", ["{kati:kati/1v}{ma:ma/1vv}{jjuti:jjut/1vn}{ksa:ksaq/1nn}{it:it/tn-nom-p}"]),
    ("katimajjutiksanut", ["{kati:kati/1v}{ma:ma/1vv}{jjuti:jjut/1vn}{ksa:ksaq/1nn}{nut:nut/tn-dat-p}"]),
    ("katimajjutiksaq", ["{kati:kati/1v}{ma:ma/1vv}{jjuti:jjut/1vn}{ksaq:ksaq/1nn}"]),
    ("pijjutilik", ["{pi:pi/1v}{jjuti:jjut/1vn}{lik:lik/1nn}"]),
    ("pijjutigillugit", ["{pi:pi/1v}{jjuti:jjut/1vn}{gi:gi/1nv}{llugit:lugit/tv-part-1s-3p-prespas}"]),
    ("pijjutigillugu", ["{pi:pi/1v}{jjuti:jjut/1vn}{gi:gi/1nv}{llugu:lugu/tv-part-1s-3s-prespas}"]),
    ("pijjutiqaqtunik", ["{pi:pi/1v}{jjuti:jjut/1vn}{qaq:qaq/1nv}{tu:juq/1vn}{nik:nik/tn-acc-p}"]),
    ("pijjutiqaqtuq", ["{pi:pi/1v}{jjuti:jjut/1vn}{qaq:qaq/1nv}{tuq:juq/1vn}"]),
    ("pijjutiqaqtut", ["{pi:pi/1v}{jjuti:jjut/1vn}{qaq:qaq/1nv}{tut:jut/tv-ger-3p}"]),
    ("ministaa", ["{minista:minista/1n}{a:k/tn-nom-d}"]),
    ("uqaqtii", ["{uqaq:uqaq/1v}{ti:ji/1vn}{i:k/tn-nom-d}"]),
    ("iksivautaa", ["{iksiva:iksiva/1v}{uta:ut/1vn}{a:k/tn-nom-d}"]),
    ("issivautaa", ["{issiva:iksiva/1v}{uta:ut/1vn}{a:k/tn-nom-d}"]),
    ("itsivautaa", ["{itsiva:iksiva/1v}{uta:ut/1vn}{a:k/tn-nom-d}"]),
    ("katimajiralaa", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}"]),
    ("katimajiralaangit", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{ngit:ngit/tn-nom-p-4s}"]),
    ("katimajiralaani", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{ni:ni/tn-loc-p}"]),
    ("katimajiralaanit", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{nit:nit/tn-abl-p}"]),
    ("katimajiralaanut", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{nut:nut/tn-dat-p}"]),
    ("katimajiralaat", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{t:it/tn-nom-p}"]),
    ("arraaguttinni", ["{arraagu:arraagu/1n}{ttinni:ptingni/tn-loc-s-1d}"]),
    ("kingunittinni", ["{kinguni:kinguni/1n}{ttinni:ptingni/tn-loc-s-1d}"]),
    ("nunalittinni", ["{nuna:nuna/1n}{li:lik/1nn}{ttinni:ptingni/tn-loc-s-1d}"]),
    ("nunattinni", ["{nuna:nuna/1n}{ttinni:ptingni/tn-loc-s-1d}"]),
    ("sivunittinni", ["{sivuni:sivuni/1n}{ttinni:ptingni/tn-loc-s-1d}"]),
    ("pigiaqtitait", ["{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{ta:jaq/1vn}{it:it/tn-nom-p}"]),
    ("pigiaqtitamut", ["{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{ta:jaq/1vn}{mut:mut/tn-dat-s}"]),
    ("pigiaqtitaq", ["{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{taq:jaq/1vn}"]),
    ("pigiaqtitaujuq", ["{pi:pi/1v}{giaq:giaq/1vv}{ti:tit/1vv}{ta:jaq/1vn}{u:u/1nv}{juq:juq/1vn}"]),
    ("apiqqusira", ["{apiq:apiq/1v}{qusi:usiq/1vn}{ra:ga/tn-nom-s-1s}"]),
    ("apiqqutiga", ["{apiq:apiq/1v}{quti:ut/1vn}{ga:ga/tn-nom-s-1s}"]),
    ("apiqqutigali", ["{apiq:apiq/1v}{quti:ut/1vn}{ga:ga/tn-nom-s-1s}{li:li/1q}"]),
    ("tungilira", ["{tungili:tungiliq/1n}{ra:ga/tn-nom-s-1s}"]),
    ("apiqqusirijara", ["{apiq:apiq/1v}{qusi:usiq/1vn}{ri:gi/1nv}{jara:jara/tv-ger-1s-3s}"]),
    ("apiqqutigijara", ["{apiq:apiq/1v}{quti:ut/1vn}{gi:gi/1nv}{jara:jara/tv-ger-1s-3s}"]),
    ("asinginnik", ["{asi:asi/1n}{nginnik:nginnik/tn-acc-p-4s}"]),
    ("ilanginnik", ["{ila:ila/1n}{nginnik:nginnik/tn-acc-p-4s}"]),
    ("katimajinginnik", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{nginnik:nginnik/tn-acc-p-4s}"]),
    ("qaujimajatuqanginnik", ["{qaujima:qaujima/1v}{ja:jaq/1vn}{tuqa:tuqaq/1nn}{nginnik:nginnik/tn-acc-p-4s}"]),
    ("atuagait", ["{atu:atuq/1v}{a:a/1vv}{ga:gaq/1vn}{it:it/tn-nom-p}"]),
    ("atuagaq", ["{atu:atuq/1v}{a:a/1vv}{gaq:gaq/1vn}"]),
    ("atuagarmik", ["{atu:atuq/1v}{a:a/1vv}{gar:gaq/1vn}{mik:mik/tn-acc-s}"]),
    ("atuagarnik", ["{atu:atuq/1v}{a:a/1vv}{gar:gaq/1vn}{nik:nik/tn-acc-p}"]),
    ("malillugu", ["{malil:malik/1v}{lugu:lugu/tv-part-1s-3s-fut}"]),
    ("nakuqmi", ["{nakuq:nakuq/1v}{mi:miik/1vn}"]),
    ("nakuqmii", ["{nakuq:nakuq/1v}{mii:miik/1vn}"]),
    ("nakurmii", ["{nakur:nakuq/1v}{mii:miik/1vn}"]),
    ("nakurmiik", ["{nakur:nakuq/1v}{miik:miik/1vn}"]),
    ("niqsunaqtuq", ["{niqsu:niqtuq/1v}{naq:naq/1vv}{tuq:juq/1vn}"]),
    ("niqtunaqtuq", ["{niqtu:niqtuq/1v}{naq:naq/1vv}{tuq:juq/1vn}"]),
    ("nirtunartuq", ["{nirtu:niqtuq/1v}{nar:naq/1vv}{tuq:juq/1vn}"]),
    ("nunavumiunut", ["{nunavu:nunavut/1n}{miu:miuq/1nn}{nut:nut/tn-dat-p}"]),
    ("nunavumiut", ["{nunavu:nunavut/1n}{miu:miuq/1nn}{t:it/tn-nom-p}"]),
    ("nunavummiunut", ["{nunavum:nunavut/1n}{miu:miuq/1nn}{nut:nut/tn-dat-p}"]),
    ("nunavummiut", ["{nunavum:nunavut/1n}{miu:miuq/1nn}{t:it/tn-nom-p}"]),
    ("gavamakkungita", ["{gavama:gavama/1n}{kku:kkut/1nn}{ngita:ngita/tn-gen-p-4s}"]),
    ("ilulingita", ["{ilu:ilu/1n}{li:lik/1nn}{ngita:ngita/tn-gen-p-4s}"]),
    ("katimajingita", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ngita:ngita/tn-gen-p-4s}"]),
    ("kajusivuq", ["{kajusi:kajusi/1v}{vuq:vuq/tv-dec-3s}"]),
    ("naammappuq", ["{naammap:naamak/1v}{puq:vuq/tv-dec-3s}"]),
    ("nuqqaqpuq", ["{nuqqaq:nuqqaq/1v}{puq:vuq/tv-dec-3s}"]),
    ("tamatuma", ["{tamatu:tamatu/rpd-ml-s}{ma:ma/tpd-gen-s}"]),
    ("katimatuinnaqtillugit", ["{kati:kati/1v}{ma:ma/1vv}{tuinnaq:tuinnaq/1vv}{tillugit:tillugit/tv-part-3p}"]),
    ("tusarumatuinnaqtunga", ["{tusa:tusaq/1v}{ruma:juma/1vv}{tuinnaq:tuinnaq/1vv}{tunga:junga/tv-ger-1s}"]),
    ("ajjaqsiji", ["{ajjaq:arjaq/1v}{si:si/1vv}{ji:ji/1vn}"]),
    ("ajjaqsijii", ["{ajjaq:arjaq/1v}{si:si/1vv}{ji:ji/1vn}{i:k/tn-nom-d}"]),
    ("tigusinirmut", ["{tigu:tigu/1v}{si:si/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}"]),
    ("aulattijiit", ["{aulat:aulat/1v}{ti:si/1vv}{ji:ji/1vn}{it:it/tn-nom-p}"]),
    ("aulattinirmut", ["{aulat:aulat/1v}{ti:si/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}"]),
    ("pivalliatittinirmut", ["{pi:pi/1v}{vallia:vallia/1vv}{tit:tit/1vv}{ti:si/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}"]),
    ("akulliq", ["{aku:aku/1n}{lliq:&iq/1nn}"]),
    ("kingulliqpaami", ["{kingu:kingu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mi:mi/tn-loc-s}"]),
    ("kingulliqpaamik", ["{kingu:kingu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mik:mik/tn-acc-s}"]),
    ("kingulliqpaamit", ["{kingu:kingu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mit:mit/tn-abl-s}"]),
    ("kingulliqpaaq", ["{kingu:kingu/1n}{lliq:&iq/1nn}{paaq:paaq/1nn}"]),
    ("kingullirmi", ["{kingu:kingu/1n}{llir:&iq/1nn}{mi:mi/tn-loc-s}"]),
    ("sivulliqpaami", ["{sivu:sivu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mi:mi/tn-loc-s}"]),
    ("sivulliqpaamik", ["{sivu:sivu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mik:mik/tn-acc-s}"]),
    ("sivulliqpaamit", ["{sivu:sivu/1n}{lliq:&iq/1nn}{paa:paaq/1nn}{mit:mit/tn-abl-s}"]),
    ("sivulliqpaaq", ["{sivu:sivu/1n}{lliq:&iq/1nn}{paaq:paaq/1nn}"]),
    ("sivullirmi", ["{sivu:sivu/1n}{llir:&iq/1nn}{mi:mi/tn-loc-s}"]),
    ("sivullirmik", ["{sivu:sivu/1n}{llir:&iq/1nn}{mik:mik/tn-acc-s}"]),
    ("matuiqsinirmut", ["{matu:matu/1n}{iq:iq/1nv}{si:si/1vv}{nir:niq/2vn}{mut:mut/tn-dat-s}"]),
    ("matuiqtauninga", ["{matu:matu/1n}{iq:iq/1nv}{ta:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{nga:nga/tn-nom-s-4s}"]),
    ("matuirutimut", ["{matu:matu/1n}{i:iq/1nv}{ruti:ut/1vn}{mut:mut/tn-dat-s}"]),
    ("piluaqtumi", ["{pi:pi/1v}{luaq:luaq/1vv}{tu:juq/1vn}{mi:mi/tn-loc-s}"]),
    ("piluaqtumik", ["{pi:pi/1v}{luaq:luaq/1vv}{tu:juq/1vn}{mik:mik/tn-acc-s}"]),
    ("piluaqtumit", ["{pi:pi/1v}{luaq:luaq/1vv}{tu:juq/1vn}{mit:mit/tn-abl-s}"]),
    ("aanniaviliaqtunut", ["{aannia:aanniaq/1v}{vi:vik/3vn}{liaq:liaq/2nv}{tu:juq/1vn}{nut:nut/tn-dat-p}"]),
    ("aippaanik", ["{aippa:aippaq/1n}{anik:nganik/tn-acc-s-4s}"]),
    ("asianik", ["{asi:asi/1n}{anik:nganik/tn-acc-s-4s}"]),
    ("maani", ["{ma:ma/rad-ml}{ani:ani/tad-loc}"]),
    ("taikunga", ["{taik:taik/rad-sc}{unga:unga/tad-dat}"]),
    ("taakkuninga", ["{taakku:taakku/rpd-sc-p}{ninga:ninga/tpd-acc-p}"]),
    ("taakkununga", ["{taakku:taakku/rpd-sc-p}{nunga:nunga/tpd-dat-p}"]),
    ("taakkunani", ["{taakku:taakku/rpd-sc-p}{nani:nani/tpd-loc-p}"]),
    ("taakkunanngat", ["{taakku:taakku/rpd-sc-p}{nanngat:nanngat/tpd-abl-p}"]),
    ("taiksumani", ["{taiksu:taiksu/rpd-sc-s}{mani:mani/tpd-loc-s}"]),
    ("tamatuminga", ["{tamatu:tamatu/rpd-ml-s}{minga:minga/tpd-acc-s}"]),
    ("tamatumunga", ["{tamatu:tamatu/rpd-ml-s}{munga:munga/tpd-dat-s}"]),
    ("tamaksuminga", ["{tamaksu:tamaksu/rpd-ml-s}{minga:minga/tpd-acc-s}"]),
    ("tamaksumunga", ["{tamaksu:tamaksu/rpd-ml-s}{munga:munga/tpd-dat-s}"]),
    ("tamannali", ["{tamanna:tamanna/pd-ml-s}{li:li/1q}"]),
    ("tamannalu", ["{tamanna:tamanna/pd-ml-s}{lu:lu/1q}"]),
    ("inulimaanut", ["{inu:inuk/1n}{limaa:limaaq/1nn}{nut:nut/tn-dat-p}"]),
    ("kikkulimaanut", ["{kikku:kikkut/1p}{limaa:limaaq/1nn}{nut:nut/tn-dat-p}"]),
    ("kikkulimaat", ["{kikku:kikkut/1p}{limaa:limaaq/1nn}{t:it/tn-nom-p}"]),
    ("piqujaq", ["{pi:pi/1v}{qu:qu/2vv}{jaq:jaq/1vn}"]),
    ("uqarumajunga", ["{uqa:uqaq/1v}{ruma:juma/1vv}{junga:junga/tv-ger-1s}"]),
    ("uqarumavunga", ["{uqa:uqaq/1v}{ruma:juma/1vv}{vunga:vunga/tv-dec-1s}"]),
    ("atuliqujaujuq", ["{atu:atuq/1v}{li:li/2vv}{qu:qu/2vv}{ja:jaq/1vn}{u:u/1nv}{juq:juq/1vn}",
        "{atu:atuq/2v}{li:li/2vv}{qu:qu/2vv}{ja:jaq/1vn}{u:u/1nv}{juq:juq/1vn}"]),
    ("naasautaa", ["{naasa:naasaq/1v}{uta:ut/1vn}{a:nga/tn-nom-s-4s}"]),
    ("uqaalautaa", ["{uqa:uqaq/1v}{ala:allak/1vv}{uta:ut/1vn}{a:nga/tn-nom-s-4s}"]),
    ("naammasaqtuit", ["{naamma:naamak/1v}{saq:ksaq/2vv}{tu:juq/1vn}{it:it/tn-nom-p}"]),
    ("naammasaqtut", ["{naamma:naamak/1v}{saq:ksaq/2vv}{tut:jut/tv-ger-3p}"]),
    ("qaujimajuinnaugatta", ["{qaujima:qaujima/1v}{ju:juq/1vn}{inna:innaq/1nn}{u:u/1nv}{gatta:gapta/tv-caus-1p}"]),
    ("atuni", ["{atuni:atunit/1n}"]),
    ("kinngarni", ["{kinngar:kinngait/1n}{ni:ni/tn-loc-p}"]),
    ("ukiuqtaqtumi", ["{ukiuqtaqtu:ukiuqtaqtuq/1n}{mi:mi/tn-loc-s}"]),
    ("saqqitaujuq", ["{saqqi:saqqik/1v}{ta:jaq/1vn}{u:u/1nv}{juq:juq/1vn}"]),
    ("saqqitaujut", ["{saqqi:saqqik/1v}{ta:jaq/1vn}{u:u/1nv}{jut:jut/tv-ger-3p}"]),
    ("saqqitauningit", ["{saqqi:saqqik/1v}{ta:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{ngit:ngit/tn-nom-p-4s}"]),
    ("saqqitausimajuq", ["{saqqi:saqqik/1v}{ta:jaq/1vn}{u:u/1nv}{sima:sima/1vv}{juq:juq/1vn}"]),
    ("amisuummata", ["{amisu:amisu/1n}{u:u/1nv}{mmata:mata/tv-caus-4p}"]),
    ("qaujimammata", ["{qaujima:qaujima/1v}{mmata:mata/tv-caus-4p}"]),
    ("gavamaulluta", ["{gavama:gavama/1n}{u:u/1nv}{lluta:luta/tv-part-1p-prespas}"]),
    ("katitsutik", ["{katit:katit/1v}{sutik:lutik/tv-part-3p-prespas}"]),
    ("malillugit", ["{malil:malik/1v}{lugit:lugit/tv-part-1s-3p-fut}"]),
    ("ministaullunga", ["{minista:minista/1n}{u:u/1nv}{llunga:lunga/tv-part-1s-prespas}"]),
    ("qaujimallunga", ["{qaujima:qaujima/1v}{llunga:lunga/tv-part-1s-prespas}"]),
    ("iglulirijirjuakkut", ["{iglu:iglu/1n}{liri:liri/1nv}{ji:ji/1vn}{rjua:juaq/1nn}{kkut:kkut/1nn}"]),
    ("illulirijirjuakkut", ["{illu:iglu/1n}{liri:liri/1nv}{ji:ji/1vn}{rjua:juaq/1nn}{kkut:kkut/1nn}"]),
    ("ilinniartulirijiit", ["{ilinniar:ilinniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{it:it/tn-nom-p}"]),
    ("iqqaqtuivilirijikkut", ["{iqqaq:iqqaq/1v}{tu:tuq/1vv}{i:i/1vv}{vi:vik/3vn}{liri:liri/1nv}{ji:ji/1vn}{kkut:kkut/1nn}"]),
    ("iqqaqtuivilirijikkunnut", ["{iqqaq:iqqaq/1v}{tu:tuq/1vv}{i:i/1vv}{vi:vik/3vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}"]),
    ("kamagijalik", ["{kama:kama/1v}{gi:gi/4vv}{ja:jaq/1vn}{lik:lik/1nn}"]),
    ("ilitarijauningit", ["{ilita:ilitaq/1v}{ri:gi/4vv}{ja:jaq/1vn}{u:u/1nv}{ni:niq/2vn}{ngit:ngit/tn-nom-p-4p}"]),
    ("aaqqiumainnarutinut", ["{aaqqi:aaqqik/1v}{uma:ma/1vv}{inna:innaq/2vv}{ruti:ut/1vn}{nut:nut/tn-dat-p}"]),
    ("katimajiralaanguinnaqtut", ["{kati:kati/1v}{ma:ma/1vv}{ji:ji/1vn}{ralaa:ralaaq/1nn}{ngu:u/1nv}{innaq:innaq/2vv}{tut:jut/tv-ger-3p}"]),
    ("katujjiqatigiingit", ["{katujji:katujji/1v}{qati:qati/1vn}{gii:giik/1nn}{ngit:ngit/tn-nom-p-4s}"]),
    ("katujjiqatigiit", ["{katujji:katujji/1v}{qati:qati/1vn}{gii:giik/1nn}{t:it/tn-nom-p}"]),
    ("tusaajitiguuqtuq", ["{tusaaji:tusaaji/1n}{tigu:tigut/tn-via-p}{uq:uq/1nv}{tuq:juq/1vn}"]),
    ("tusaajitiguurunniiqtuq", ["{tusaaji:tusaaji/1n}{tigu:tigut/tn-via-p}{u:uq/1nv}{runniiq:junniiq/1vv}{tuq:juq/1vn}"]),
    ("qallunaat", ["{qallunaa:qaplunaaq/1n}{t:it/tn-nom-p}"]),
    ("qallunaatitut", ["{qallunaaq:qaplunaaq/1n}{titut:titut/tn-sim-p}"]),
    ("illaqtut", ["{illaq:iglaq/1v}{tut:jut/tv-ger-3p}"]),
    ("tullia", ["{tulli:tugli/1n}{a:nga/tn-nom-s-4s}"]),
    ("tungilia", ["{tungili:tungiliq/1n}{a:nga/tn-nom-s-4s}"]),
    ("mannaujuq", ["{manna:manna/pd-ml-s}{u:u/1nv}{juq:juq/1vn}"]),
    ("ilitaqsiniq", ["{ilitaq:ilitaq/1v}{si:si/3vv}{niq:niq/2vn}"]),
    ("qujannami", ["{quja:quja/1v}{nna:naq/1vv}{mi:miik/1vn}"]),
    ("alianaigusukpunga", ["{alianai:alianait/1v}{gusuk:gusuk/1vv}{punga:vunga/tv-dec-1s}"]),
    ("qulluktuq", ["{qulluktuq:kugluktuk/1n}"]),
    ("tukisinaliqtissimajuq", ["{tukisi:tukisi/1v}{na:naq/1vv}{liq:liq/1vv}{tis:tit/1vv}{sima:sima/1vv}{juq:juq/1vn}"]),
    ("maligaliuqtii", ["{mali:malik/1v}{ga:gaq/1vn}{liuq:liuq/1nv}{ti:ji/1vn}{i:it/tn-nom-p}"]),
    ("titiraqtii", ["{titi:titiq/1v}{raq:raq/1vv}{ti:ji/1vn}{i:it/tn-nom-p}"]),
    ("uqartii", ["{uqar:uqaq/1v}{ti:ji/1vn}{i:it/tn-nom-p}"]),
    ("katimautitsa", ["{kati:kati/1v}{ma:ma/1vv}{uti:ut/1vn}{tsa:ksaq/1nn}"]),
    ("aglattiu", ["{aglat:allak/1v}{ti:ji/1vn}{u:up/tn-gen-s}"]),
    ("inuktitut", ["{inuk:inuk/1n}{titut:titut/tn-sim-p}"]),
    ("makkuktunut", ["{makkuk:makkuk/1v}{tu:juq/1vn}{nut:nut/tn-dat-p}"]),
    ("makkuktut", ["{makkuk:makkuk/1v}{tut:jut/tv-ger-3p}"]),
    ("maliktugit", ["{malik:malik/1v}{tugit:lugit/tv-part-1s-3p-prespas}"]),
    ("uuktuutigilugu", ["{uuk:uuk/1v}{tu:tuq/1vv}{uti:ut/1vn}{gi:gi/1nv}{lugu:lugu/tv-part-1s-3s-fut}"]),
    ("aviktursimajuni", ["{avik:avik/1v}{tur:tuq/1vv}{sima:sima/1vv}{ju:juq/1vn}{ni:ni/tn-loc-p}"]),
    ("tunngasugitsi", ["{tunnga:tunnga/1v}{su:suk/1vv}{gitsi:gipsi/tv-imp-2p}"]),
    ("maligaliurviliarsimajut", ["{maligaliurvi:maligaliurvik/1n}{liar:liaq/2nv}{sima:sima/1vv}{jut:jut/tv-ger-3p}"]),
    ("qanuittuni", ["{qanu:qanuq/1a}{it:it/3nv}{tu:juq/1vn}{ni:ni/tn-loc-p}"]),
    ("qanuittunik", ["{qanu:qanuq/1a}{it:it/3nv}{tu:juq/1vn}{nik:nik/tn-acc-p}"]),
]

GOLD_MORPHEME_RE = re.compile(r"\{[^:]+:([^/]+)/([^}]+)\}")


def parse_gold(decomposition: str) -> list[tuple[str, str]]:
    """'{aki:aki/1n}{nga:nga/tn-nom-s-4s}' -> [('aki','1n'), ('nga','tn-nom-s-4s')]"""
    return GOLD_MORPHEME_RE.findall(decomposition)


def parse_hfst_analysis(analysis: str) -> list[tuple[str, str]]:
    """'aki+1n++nga+tn-nom-s-4s' -> [('aki','1n'), ('nga','tn-nom-s-4s')]"""
    pairs = []
    for morpheme in analysis.split("++"):
        canonical, _, tag_id = morpheme.partition("+")
        pairs.append((canonical, tag_id))
    return pairs


def hfst_analyses_weighted(word: str, lenient: bool = False) -> list[tuple[str, float]]:
    """Runs hfst-lookup on `word`, returns (analysis, weight) pairs (upper-side
    output plus its own weight), in the order hfst-lookup printed them,
    excluding unrecognized-word placeholders (HFST prints "word+?" with
    weight inf).

    `lenient`: phonology.xfscript's own LENIENT rule (mirroring the real
    analyzer's own --lenient-decomps extension) gives every ordinary
    analysis weight 0.0, but ALSO adds parallel weight-1.0 paths for
    vowel-final words that assume a final k/p/q/t was silently dropped --
    see that rule's own comment for why weight, not a tag, is what
    distinguishes them. Default False (weight 0 only) excludes those
    paths entirely; pass lenient=True to also include the weight-1.0
    ones."""
    result = subprocess.run(
        ["hfst-lookup", str(ANALYSER)],
        input=word + "\n",
        capture_output=True,
        text=True,
        check=True,
    )
    max_weight = 1.0 if lenient else 0.0
    analyses = []
    for line in result.stdout.splitlines():
        fields = line.split("\t")
        if len(fields) != 3:
            continue
        _, analysis, weight_str = fields
        if weight_str.strip() == "inf":
            continue
        weight = float(weight_str)
        if weight > max_weight:
            continue
        analyses.append((analysis, weight))
    return analyses


def hfst_analyses(word: str, lenient: bool = False) -> list[str]:
    """Same as hfst_analyses_weighted, but returns just the analysis
    strings -- for the many existing callers that only check PRESENCE of
    a correct decomposition (full_corpus_check.py, snapshot_correct.py,
    this module's own GOLD_CASES check) and never needed weight."""
    return [analysis for analysis, _weight in hfst_analyses_weighted(word, lenient=lenient)]


def categorize(gold_parses: list, hfst_parses: list) -> str:
    """Shared with full_corpus_check.py: same 4 categories as the existing
    919-word :cli regression suite."""
    if not hfst_parses:
        return "no-decomps"
    if hfst_parses[0] in gold_parses:
        return "first-decomposition-correct"
    if any(p in gold_parses for p in hfst_parses):
        return "correct-but-not-first"
    return "correct-not-present"


def main():
    histogram = {
        "first-decomposition-correct": 0,
        "correct-but-not-first": 0,
        "correct-not-present": 0,
        "no-decomps": 0,
    }

    for word, gold_strings in GOLD_CASES:
        gold_parses = [parse_gold(g) for g in gold_strings]
        analyses = hfst_analyses(word)
        hfst_parses = [parse_hfst_analysis(a) for a in analyses]

        category = categorize(gold_parses, hfst_parses)
        histogram[category] += 1
        print(f"{word:28s} {category:28s} {analyses}")

    print()
    print(f"Milestone 4 histogram, out of {len(GOLD_CASES)}:")
    for category, count in histogram.items():
        print(f"  {count:2d}  {category}")


if __name__ == "__main__":
    main()
