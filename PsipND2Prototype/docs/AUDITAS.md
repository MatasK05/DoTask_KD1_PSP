# DoTask II etapo patikros ir įrodymai

Naujausia patikra: 2026-11-15; ankstesnių bandymų data – 2026-11-11. Šis dokumentas skiria įgyvendinimą nuo faktiškai įvykdytos patikros. Galutinis pažymys neprognozuojamas. I etapo patvirtinimo būsena nežinoma; ją tikrina studentas.

## 2026-11-15: pakartotinė patikra ir naudotojo prašytas sustabdymas

**Automatinės patikros sustabdytos naudotojo prašymu. Du pilni galutinės versijos ciklai nebaigti.** Toliau pateikti 2026-11-11 rezultatai yra ankstesnių versijų istorija, o ne šio naujo dviejų ciklų prašymo užbaigimas.

Perskaityti abu II reikalavimų PDF puslapiai. Sutrumpinta README paleidimo dalis, pašalinti asmeniniai katalogai. Papildytas [gynimo gidas](GYNIMAS.md) ir sukurta [PNG schema](SCHEMA.png), 2800 × 2060 taškų. Visuose 27 Java failuose pridėti trumpi lietuviški atsakomybių komentarai; pagrindiniame algoritme paaiškintas minučių skirstymas. `JsonConfiguration` taisyklės išdėstytos atskiromis aiškiomis eilutėmis.

Vykdant tikras README PowerShell komandas aptikta problema: Windows PowerShell 5.1 klaidingai dekodavo dalį lietuviškų JSON tekstų, kai atsakyme nebuvo aiškios koduotės. Sėkmingi ir klaidų API atsakymai dabar nurodo `application/json;charset=UTF-8`. Pridėtas nuolatinis regresijos testas `PlanningApiIT.jsonResponsesDeclareUtf8AndPreserveLithuanianText`. README klaidos rodymui pridėtas atsakymo srauto perskaitymas, kai senesnis PowerShell neužpildo `ErrorDetails`.

| Patikra | Faktiškai atlikta |
| --- | --- |
| Iki UTF-8 pataisos, po komentarų papildymo | PostgreSQL ir H2 `clean verify`: po **40/40**, be klaidų ar praleistų testų |
| Tikros programos HTTP su `dotask_test` iki UTF-8 pataisos | **21 scenarijus, 2 499 užklausos, 2 943 patikros PASS**; apimta ir testų duomenų paruoša bei valymas |
| Tame HTTP bandyme | Tikslūs I plano pavyzdžiai, 120 / 60 / 180 likučiai, netinkama įvestis, izoliacija, 1 000 / 1 001 riba, laiko ribos, 60 lygiagrečiai vykdytų perskaičiavimų su 120 skaitymų |
| Programos paleidimas iš naujo per `spring-boot:run` iki UTF-8 pataisos | Dalinis grafikas ir įvestis išliko: **1 papildomas scenarijus, 6 užklausos, 7 patikros PASS** |
| README PowerShell demonstracija iki pataisos | Funkciniai rezultatai teisingi: 400, 404 skaitymui / PUT / DELETE, nepakitęs grafikas ir Klubo įrašas; būtent čia pastebėta lietuviškų raidžių rodymo problema |
| Po UTF-8 pataisos: `.\mvnw.cmd clean verify` su PostgreSQL | **41/41 (26 branduolio + 15 integracinių), 0 klaidų, 0 praleistų**; 2026-11-15 16:10:59 +02:00 |
| Po UTF-8 pataisos: ta pati komanda su H2 | **41/41**, 0 klaidų ir praleistų; 2026-11-15 16:11:30 +02:00 |
| Antras pilnas galutinės versijos ciklas ir gyvas HTTP pakartojimas po UTF-8 pataisos | **NEBAIGTA / NOT RUN** – naudotojas sustabdė automatines patikras |
| JavaScript sintaksė, dokumentų nuorodos, Java komentarų buvimas | `node --check` ir vietinės dokumentų patikros PASS |
| Programos paleidimas naudotojui po pataisos | `http://localhost:8080`: puslapis HTTP 200, Fakulteto kontekstas, 4 pradinės sesijos; palikta veikti naudotojo bandymui |
| Vizuali naršyklės sąsajos patikra | **NOT RUN** – valdymo įrankiuose neprieinama naršyklė; PNG schemos vaizdas peržiūrėtas atskirai |

Visos automatinės duomenų keitimo patikros naudojo atskirą `dotask_test` bazę ir 8081 portą. Įprastos `dotask` bazės duomenų bei sekų išrašai prieš bandymus ir prieš programos paleidimą naudotojui sutapo. Po paleidimo 8080 portu naudotojas galėjo pats redaguoti programos duomenis. Laikini pakartotinės patikros failai ir `target` išvalyti; nuolatiniai JUnit testai išsaugoti.

Vėlesniu naudotojo prašymu uždaryta ir rankinio bandymo aplinka: sustabdyta DoTask programa bei jai skirtas vietinis PostgreSQL klasteris. Pašalinta `.local/run` programos kopija, jos žurnalai ir PID failas. Duomenų bazės, JDK, PostgreSQL įrankiai, vietinė konfigūracija, šaltiniai, reikalaujami testai ir gynimo dokumentai išsaugoti. Kitą kartą programą paleiskite pagal README. Po prašymo stabdyti automatinius testus jie nebuvo kartojami.

GitHub paliktas naudotojui: **NOT PUSHED**, commit nesukurtas. I etapo patvirtinimas šioje sesijoje nepatvirtintas.

## Ankstesnė 2026-11-11 išplėstinė patikra po įgyvendinimo

Vartotojo prašymu tikrintas ne vien JUnit rezultatas, bet ir supakuota programa per tikrą HTTP, naudojant atskirą `dotask_test` PostgreSQL bazę ir portą 8081. Įprastos `dotask` bazės duomenų ir sekų išrašai prieš bei po bandymų sutapo. Laikini bandymų duomenys buvo kuriami tik testų bazėje.

Rastos ir pataisytos klaidos:

- JSON `done: 1` būdavo paverčiamas į `true`, skaičiai į tekstą, o papildomas tekstas po JSON objekto ignoruojamas. Dabar tikrinami tikrieji laukų tipai, atmetami pasikartojantys JSON laukai ir perteklinis turinys (`400`).
- Netinkamas `Content-Type` grąžindavo `500`. Dabar grąžinamas lietuviškas `415`, o nesuderinamas `Accept` – `406` dar prieš duomenų keitimą.
- PostgreSQL negali tiksliai išsaugoti nanosekundžių. API dabar atmeta smulkesnį nei mikrosekundės tikslumą, kad prieinamumo pradžia ar terminas nebūtų nepastebimai suapvalinti.
- Nepavykus užkrauti kito kliento, pasirinkimas galėdavo rodyti naują klientą, o lentelės – ankstesnį. Dabar nesėkmės atveju pasirinkimas grįžta prie paskutinio sėkmingai parodyto konteksto.

| Patikra | Faktinis rezultatas |
| --- | --- |
| Pradinis pakartotinis PostgreSQL `clean verify` prieš pataisas | 37 testai PASS; papildomi bandymai aptiko aukščiau nurodytas spragas |
| Galutinis PostgreSQL `.\mvnw.cmd clean verify` | **40 testų: 26 branduolio + 14 integracinių; 0 klaidų, 0 praleistų**, 13:49:11 +02:00 |
| Galutinis H2 `.\mvnw.cmd clean verify`, nenustačius `TEST_DB_*` | **Tie patys 40 testų PASS**, 0 klaidų ir praleistų, 13:49:40 +02:00 |
| Gyvas supakuotos programos HTTP / PostgreSQL bandymas | **18 scenarijų, 3 570 HTTP užklausų, 4 183 patikros PASS**; užklausų skaičius apima ir bandymų duomenų paruošimą bei valymą |
| Lygiagretus veikimas tame HTTP bandyme | 60 perskaičiavimų, vienu metu iki 12 užklausų; po to 20 įvesties pakeitimų su perskaičiavimais ir 180 lygiagrečių grafiko skaitymų: nebuvo dubliuotų, tuščių ar sumaišytų rezultatų |
| Užduočių skaičiaus riba per tikrą HTTP ir SQL | 1 000 priimta, 1 001 atmesta; grafikas lygintas su būsena prieš pat atmestą skaičiavimą, įskaitant jau teisingai nustatytą `stale=true` po galiojančio papildomos užduoties sukūrimo |
| Atsitiktiniai branduolio scenarijai | 2 000 fiksuotos sėklos `2026111107` įvesčių; tas pats rinkinys pakartotas sistemos, `UTC` ir `Pacific/Auckland` JVM laiko juostose: **18 000 skaičiavimų, 398 421 patikra PASS** |
| Nepriklausomas branduolio palyginimas | Kiekvienoje iš 3 aplinkų 400 rezultatų lyginti su visų galimų 15 min. pradžių ir trukmių paieška; papildomai 29 netinkamos įvestys ir 4 ekstremalūs atvejai kiekvienoje aplinkoje |
| Tikro `app.js` vykdymas DOM simuliatoriuje | **28 scenarijai PASS** su imituotu HTTP; tas pats rinkinys pakartotas `TZ=UTC` ir `TZ=America/Los_Angeles`; `node --check` PASS |

Gyvas HTTP bandymas apėmė visus penkis I plano atvejus, dalinį 180 min. rezultatą, kartojimą, CRUD, neteisingus laukus ir tipus, neteisingą JSON, kliento bei savininko klastojimą, svetimus ir spėjamus ID, persidengimus, 15 min. apvalinimą, trumpus tarpus, vidurnaktį, horizonto ribą, natūralias pertraukas, laikrodžio persukimą, duomenų bazės laiko tikslumą ir saugų demo atkūrimą. Kito kliento duomenys liko identiški.

DOM patikra tikrino formas, užklausų turinį ir konteksto antraštes, Vilniaus laiko rodymą, dalinį rezultatą, klaidų tekstus, persijungimo nesėkmę ir atsigavimą, dvigubų paspaudimų blokavimą bei įvesto HTML rodymą kaip paprastą tekstą. **Tai nėra vizuali naršyklės patikra; ji tebėra NOT RUN**, nes naršyklės valdymas šiame seanse neprieinamas. Atsitiktiniai ir lygiagretūs bandymai nėra formalus optimalumo ar našumo įrodymas.

Regresijos paliktos užduočiai reikalingame `PlanningApiIT`: `malformedJsonAndScalarTypesAreRejectedWithoutSaving`, `unsupportedHttpFormatsReturnClientErrorsBeforeChangingData`, `timestampPrecisionNeverSilentlyMovesInputBeforeTheRequestedTime`. Papildomi vienkartiniai Java, Python ir DOM bandymų failai, jų priklausomybės bei žurnalai pašalinti; sugeneruotas `target` išvalytas. JUnit šaltiniai ir jų konfigūracija palikti, todėl visas rinkinys atkuriamas komanda `clean verify`.

## Aplinka

| Įrankis | Fiksuota / naudota versija |
| --- | --- |
| JDK | Microsoft OpenJDK 21.0.13.0.2+1 |
| Maven | 3.9.11 |
| Maven Wrapper | 3.3.4, `only-script` |
| Spring Boot | 3.5.16, standartinis parent priklausomybių valdymas |
| PostgreSQL | 17.11, atskiras vietinis procesas `127.0.0.1:55432` |
| Darbo bazė | `dotask`, atskiras savininkas |
| PostgreSQL testų bazė | `dotask_test`, atskiras savininkas ir prisijungimas |
| PostgreSQL JDBC | 42.7.11 |
| Spring Framework | 6.2.19 |
| JUnit Jupiter | 5.12.2 |
| Surefire / Failsafe | 3.5.6 |
| Alternatyvus testų adapteris | H2 2.3.232 atmintyje, tik `test` priklausomybė; nėra PostgreSQL atitikties garantija |
| IntelliJ | Esama IntelliJ IDEA 2026.2.3 aplinka; Maven projektas su JDK 21 |

## Pradinės įgyvendinimo patikros iki išplėstinio bandymo

| Komanda / patikra | Rezultatas | Apimtis |
| --- | --- | --- |
| `.\mvnw.cmd '-Dtest=SchedulingServiceTest' test` | **PASS: 26 vykdymai, 0 klaidų, 0 praleistų** | Grynas Java planuoklis, visi I plano skaičiavimo atvejai ir ribos |
| Pirmas `.\mvnw.cmd clean verify` su PostgreSQL | **PASS: 36 vykdymai (26 + 10), 0 klaidų, 0 praleistų** | Pilna pradinio rinkinio patikra; vėliau papildytas kito to paties tenant naudotojo izoliacijos atvejis |
| `.\mvnw.cmd -q verify` su `TEST_DB_URL` į PostgreSQL `dotask_test` | **PASS: 37 vykdymai (26 + 11), 0 klaidų, 0 praleistų** | Grynas modulis ir HTTP / SQL integraciniai testai, įskaitant izoliaciją ir rollback |
| Antras `.\mvnw.cmd clean verify` su PostgreSQL | **PASS: 37 vykdymai (26 + 11), 0 klaidų, 0 praleistų** | Tuometinis rinkinys; 2026-11-11 13:37:32 +02:00, Maven trukmė 7,460 s |
| Tiesioginis HTTP prieš paleistą programą su PostgreSQL | **PASS** | Tikslus 4 sesijų grafikas; identiškas pakartojimas; pašalintas 1002 → 2 sesijos, A 180 / B 0; −15 PUT → 400 ir identiškas GET grafikas |
| Tiesioginis svetimo ID 201 HTTP bandymas | **PASS** | Fakulteto GET / PUT / DELETE → 404; Klubo užduotis ir grafikas nepakitę |
| HTTP `/`, `/app.js`, `/style.css` | **PASS: 200** | Statiniai failai pateikiami ta pačia programa |
| Programos sustabdymas ir pakartotinis `.\mvnw.cmd spring-boot:run` | **PASS** | Dalinis grafikas ir pašalinto 1002 nebuvimas išliko identiški; pakartotinis demo inicijavimas neperrašė duomenų |
| `node --check src/main/resources/static/app.js` | **PASS** | JavaScript sintaksė |
| Vizuali naršyklės patikra | **NOT RUN** | Kompiuterio valdymo įrankių inventoriuje nebuvo prieinamos naršyklės; vien HTTP 200 nėra vizualios patikros įrodymas |

Testų rezultatų failai: `target/surefire-reports/lt.dotask.domain.SchedulingServiceTest.txt` ir `target/failsafe-reports/lt.dotask.application.PlanningApiIT.txt`. Jie yra generuojami build rezultatai ir neįkeliami į Git. H2 adapterio integracinė patikra taip pat buvo sėkminga iki papildomo to paties tenant kito naudotojo testo pridėjimo (tuomet 26 + 10 = 36); galutinis 37 testų rinkinys patikrintas su PostgreSQL. Vien tik sukompiliuotas kodas ar H2 patikra nelaikomi PostgreSQL demonstracija.

Taigi prašymas projektą patikrinti bent du kartus įvykdytas dviem pilnais švariais PostgreSQL build paleidimais. Tarp jų atlikta taisyklių, nuosavybės ir dokumentacijos peržiūra; papildytas izoliacijos atvejis. Tai nėra trys vienodos komandos vietoj reikalavimų audito.

Po pradinės demonstracijos Fakulteto demo atkurtas į bazines keturias sesijas. Bandymų programų procesai sustabdyti; programą galima paleisti README komanda.

## 1. II užduoties auditas

| Šaltinio punktas / kriterijus | Įgyvendinimo vieta ir įrodymas | Patikros būsena |
| --- | --- | --- |
| Pagrindinė funkcija – 1,50 | HTML formos → `PlanningController.plan` → `PlanningApplicationService.recalculate` → `SchedulingService.calculate` → SQL grafikas | Algoritmas ir tikras HTTP / PostgreSQL scenarijus PASS; vizuali naršyklė NOT RUN |
| Struktūra – 1,00 | `domain` neturi Spring / HTTP / SQL; `application` koordinuoja; `web` priima užklausas; `persistence` saugo; rikiavimas per `Comparator` | Kodo peržiūra atlikta |
| Testai – 0,50 | `SchedulingServiceTest` ir `PlanningApiIT`; `clean verify` apima Surefire ir Failsafe | Galutiniai 40 testų vykdymai su PostgreSQL ir H2 PASS |
| Klientų atskyrimas – 0,50 | `DemoContextResolver`, SQL pora `tenant_id + owner_user_id`, `foreignTaskCannotBeReadUpdatedOrDeletedThroughHttp` | Integracinė ir tiesioginė HTTP patikra PASS |
| Paleidimas / README – 0,50 | README, Maven Wrapper, schema, du demo rinkiniai, saugūs konfigūracijos pavyzdžiai | Instrukcijos ir tikri API adresai peržiūrėti |
| Pateikimas toje pačioje GitHub repozitorijoje | Pradinėje vietoje nebuvo `.git`; naudotojas paprašė GitHub palikti jam | **NOT PUSHED**; commit nesukurtas |
| I etapas patvirtintas | Studentas turi patikrinti prieš pateikimą | **NEPATVIRTINTA ŠIOJE SESIJOJE** |
| Gyvas paaiškinimas / mažas pakeitimas | `docs/GYNIMAS.md`, `priorityFirst()` ir tikslingas testas | Techninis pavyzdys testuojamas; studento pasiruošimas nevertintas |

III etapui palikti prisijungimas / rolės, klientui skirta konfigūracija, konteinerizavimas ir formalūs kokybės atributų įrodymai. Android ir priminimai – vėlesnis I plano darbas. Tai patikrinta pagal II PDF 1 p. apimties pastraipą; šie papildymai nepakeitė vertinamos planavimo funkcijos darbo.

## 2. I plano taisyklių auditas

| Taisyklė | Vieta / testas | Laukiamas įrodymas |
| --- | --- | --- |
| Tikslus bazinis pavyzdys | `baseExampleMatchesAllFourSessionsAndSharedDailyLimit` | 4 tikslūs laikai, A 240, B 120, 0 likutis |
| Vienodi terminai | `equalDeadlinesUseHigherPriorityAndPreserveBreak` | B 18–19, A 19:15–20:15 |
| Prieinamumo trūkumas | `shortageLeavesExactly120Minutes` | 120 suplanuotų ir 120 likutis |
| Dienos limitas | `dailyLimitLeavesExactly60Minutes` | 120 + 60 sesijos ir 60 likutis |
| Neigiama trukmė | `rejectsInvalidUnfinishedMinutes`; `invalidEditAndInvalidCalculationLeaveExactSavedScheduleUnchanged` | Klaida ir nepakeistas senas grafikas / užduotis |
| Demo pakeitimas | `removingSecondDayLeaves180MinutesForA` | Tik pirmos dienos sesijos, A likutis 180 |
| Eilė ir stabilus ID | `TaskComparators`; `equalPriorityAndDeadlineUseStableLowestId` | Terminas → prioritetas → ID |
| Atliktos / pradelstos | `completedTasksAreOmittedAndElapsedDeadlinesRemainUnscheduled` | Atliktos praleistos; neigiamo darbo laiko nėra |
| Riba 1000 ir horizontas 1–30 | `rejects1001TasksButAccepts1000`; `rejectsHorizonOutsideOneToThirtyDays` | Viršijimas atmetamas, užduotys nenukerpamos |
| 15 min. pradžia ir trukmė | `roundsStartUpAndUsableDurationDown`; `subQuarterGapsProduceNoSessionsAndFinishNormally` | Pradžia aukštyn, trukmė žemyn, nėra begalinio ciklo |
| Persidengimų sujungimas | `overlappingAvailabilityIsMergedWithoutDuplicatedWorkTime` | Tas pats laikas nenaudojamas dukart |
| Vidurnaktis ir horizonas | `midnightSplitsDailyWorkAndPreservesBreak`; `horizonEndsExactlyAfterLocalCalendarDays` | Dienos pagal Vilnių, pusiau atviros ribos |
| Laikrodžio persukimas | `explicitOffsetsAcrossAutumnClockChangeUseElapsedMinutes`; HTTP datų testas | Aiškus poslinkis, realios minutės |
| Abi pertraukos pusės | `freeGaps`; `freeGapBeforeLaterSessionReservesBreakOnBothSides` | Tarpas ir prieš vėlesnę sesiją |
| Natūrali pertrauka | `naturalGapAlreadyProvidesBreak` | Pertrauka nepridedama antrą kartą |
| Pakartojimas | `recalculationIsDeterministicAndDoesNotConsumeRemainingWork`; integracinis bazinis testas | Tas pats grafikas, nėra dublikatų, darbas nesumažintas |
| Rezultato invariantai | `SchedulingService.validateResult` + taikomosios paslaugos nuosavybės patikra | Minučių sumos, ribos, terminai, pertraukos, dienos ir sesijos limitai |
| Atominis pakeitimas | `PlanningApplicationService.recalculate`, `failedReplacementRollsBackDeletionInsertionAndMetadata` | Nepavykus įrašymui senas grafikas visas išlieka |

Datos testuose fiksuotos 2026 m. spalį; `now()` nepakeičia demonstracijos pradžios. Likučių paaiškinimai gali nurodyti kelis apribojimus ir neskelbia, kad kitokia strategija neįmanoma.

## 3. Gynimo auditas

README pateikti tikri adresai ir PowerShell užklausos su `X-Demo-Context`, fiksuotais 101/102/201 ID. Lygiaverčiai bazinis, dalinis, klaidos ir izoliacijos HTTP scenarijai faktiškai įvykdyti prieš programą su PostgreSQL. Gynimo gide yra vieno mygtuko kelias, tikros klasės, testų metodai ir alternatyvaus `Comparator` repeticija; abi rūšiavimo išvestys patikrintos automatiniu testu.

Švarus paruošimas atliktas sukuriant atskirą vietinį PostgreSQL 17.11 klasterį per `initdb` ir dvi skirtas bazes. README standartinio Windows diegiklio eiga nebuvo atskirai vykdyta. PostgreSQL procesas nėra Windows tarnyba: po perkrovimo jį reikia paleisti scenarijumi. Vizuali demonstracija naršyklėje **NOT RUN**, nes įrankiai neturėjo naršyklės paviršiaus. Studentas turi pats patikrinti puslapio formas ir išdėstymą bei paaiškinti demonstruojamus veiksmus savais žodžiais.

Galutinei pateikimo būsenai vis dar reikalingi studento veiksmai: patikrinti I etapo patvirtinimą, peržiūrėti AI padedant sukurtą kodą, pasipraktikuoti pakeitimą ir įkelti visus šaltinius / testus / README į tą pačią GitHub repozitoriją. Kursinis nepateiktas studento vardu.
