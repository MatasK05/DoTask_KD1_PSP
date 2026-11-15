# DoTask – II etapo prototipas

DoTask iš užduočių, terminų, prioritetų ir laisvo laiko apskaičiuoja darbo sesijas bei nesuplanuotas minutes. Naudojamas deterministinis Java algoritmas; programoje nėra AI paslaugos ar API rakto.

[Gynimo gidas](docs/GYNIMAS.md) · [Schema (PNG)](docs/SCHEMA.png) · [Patikrų rezultatai](docs/AUDITAS.md)

## Paleidimas

Visas komandas vykdykite **projekto kataloge, kuriame yra `pom.xml`**: IntelliJ lange atverkite **Terminal**. Projekto vieta diske ir Windows naudotojo vardas nesvarbūs.

Jei JDK, PostgreSQL ir `config/local.ps1` jau paruošti:

1. Įsitikinkite, kad PostgreSQL veikia. Įprastam diegimui naudokite Windows PostgreSQL tarnybą. Jei naudojate pateiktą, jau inicijuotą portable aplinką, ją paleidžia ` .\scripts\Start-Database.ps1 `.
2. Tame pačiame PowerShell terminale vykdykite:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
. .\config\local.ps1
.\mvnw.cmd spring-boot:run
```

Atverkite **http://localhost:8080**. Sustabdyti – **Ctrl+C**. Pirma eilutė leidžia vietinius scenarijus tik šiame terminale, antra įkelia konfigūraciją, trečia paleidžia programą. Po paruošimo vienintelė programos paleidimo komanda yra ` .\mvnw.cmd spring-boot:run `.

Jei portable scenarijų blokuoja PowerShell, pirmą eilutę įvykdykite prieš jį. Standartinei PostgreSQL tarnybai šis scenarijus nereikalingas.

## Kodėl grafikas tuščias?

Vien laisvo laiko neužtenka: tame pačiame demo kontekste turi būti išsaugota **neatlikta užduotis**, o laisvas laikas turi patekti tarp planavimo pradžios ir užduoties termino. Po pakeitimų spauskite **„Apskaičiuoti / perskaičiuoti“** ir patikrinkite žemiau esančią lentelę **„Nesuplanuotas darbas“**.

Pavyzdžiui, demo užduočių terminai yra **2026-10-06 ir 2026-10-07**. Jei planavimo pradžią pakeisite į **2026-10-20**, šios užduotys jau bus pradelstos. Net lapkričio 15–16 d. pridėtas laisvas laikas jų termino nepratęsia. 30 dienų horizontas nuo spalio 20 d. apima šį lapkričio intervalą, tačiau neleidžia dirbti po užduoties termino.

Patikrinkite tris dalykus:

- Užduotis išsaugota sąraše ir nepažymėta „Atlikta“. Jei užduočių nėra arba visos atliktos, abi rezultato lentelės gali būti tuščios.
- Laisvo laiko yra **iki termino** ir pasirinktame planavimo laikotarpyje; reikia bent 15 tinkamų minučių.
- Visus įrašus kuriate tame pačiame kontekste („Fakultetas“ arba „Klubas“). Klaidos tekstas rodomas puslapio viršuje.

### Paprastas bandymas su savo užduotimi

Tam pačiam kontekstui išsaugokite tokią įvestį (visos datos – Europe/Vilnius):

| Laukas | Reikšmė |
| --- | --- |
| Užduoties pavadinimas | Bandymas |
| Liko darbo | 60 min. |
| Prioritetas / būsena | MEDIUM / neatlikta |
| Užduoties terminas | 2026-11-16 20:20 |
| Laisvas laikas | 2026-11-15 18:14 → 2026-11-16 20:20 |
| Planavimo pradžia / laikotarpis | 2026-11-15 18:14 / 2 dienos |

Jei tai vienintelė tinkama neatlikta užduotis ir vienintelis laisvo laiko intervalas, laukiama sesija yra **2026-11-15 18:15–19:15**. Pradžia 18:14 apvalinama aukštyn iki 18:15. Kitos tinkamos užduotys gali pakeisti skirstymą pagal jų terminus ir prioritetus. Skaičiavimas naudoja įvestą pradžią, todėl šį pavyzdį galima kartoti ir kitą dieną.

Fiksuotam I etapo pavyzdžiui naudokite **2026-10-05 18:00 ir 3 dienas**. Demo atkūrimas atsisako trinti papildomus jūsų įrašus; jų nepraraskite vien dėl noro pakartoti pavyzdį.

## Pirmas paruošimas kitame kompiuteryje

**1. JDK ir IntelliJ.** Įdiekite **JDK 21** ir **PostgreSQL 17**. IntelliJ atverkite `pom.xml` kaip Maven projektą, pasirinkite **Project SDK = 21** bei **Maven Runner JRE = 21**, paspauskite **Reload All Maven Projects**. Atskirai Maven diegti nereikia; Wrapper jį atsisiunčia. Pirmam priklausomybių atsisiuntimui reikia interneto.

**2. Dvi atskiros bazės.** Vieną kartą per PostgreSQL `psql`, prisijungę administratoriumi, vykdykite [scripts/setup-databases.sql](scripts/setup-databases.sql). Standartiniam Windows diegimui:

```powershell
& "$env:ProgramFiles\PostgreSQL\17\bin\psql.exe" -h localhost -p 5432 -U postgres -d postgres -f .\scripts\setup-databases.sql
```

Jei PostgreSQL įdiegta kitur arba naudoja kitą portą, pakeiskite tik šias reikšmes. Scenarijus sukuria `dotask` ir **atskirą** `dotask_test` su atskirais savininkais ir paprašo jų slaptažodžių. Testų bazėje nelaikykite darbo duomenų. Scenarijaus nekartokite, jei šios bazės jau sukurtos.

**3. Konfigūracija.** Jei `config/local.ps1` dar nėra, sukurkite kopiją:

```powershell
Copy-Item .\config\example.ps1 .\config\local.ps1
```

Atverkite kopiją ir įrašykite savo **JDK 21 katalogą**, DB adresą, portą ir ką tik nustatytus slaptažodžius. Esamos veikiančios kopijos neperrašykite. Pavyzdžio reikšmės:

| Kintamasis | Reikšmė |
| --- | --- |
| `JAVA_HOME` | Jūsų JDK 21 diegimo katalogas |
| `DB_HOST`, `DB_PORT` | `localhost`, `5432` (arba jūsų serverio portas) |
| `DB_NAME`, `DB_USER` | `dotask`, `dotask` |
| `DB_PASSWORD` | Vietinis programos DB slaptažodis |
| `TEST_DB_URL` | `jdbc:postgresql://localhost:5432/dotask_test` |
| `TEST_DB_USER`, `TEST_DB_PASSWORD` | `dotask_test` ir atskiras testų slaptažodis |

`config/local.ps1` nekeliamas į Git. **Failas pats neįkeliamas**: komanda `. .\config\local.ps1` nustato aplinkos kintamuosius dabartiniam terminalui. Naujam terminalui ją pakartokite. Spring juos skaito per `application.properties`; `.env` automatiškai neskaitomas. Pasirinktinis `PORT` keičia HTTP portą (numatyta 8080).

Dabar vykdykite paleidimo bloką aukščiau. `schema.sql` sukuria trūkstamas lenteles, o `DemoInitializer` įrašo demo duomenis tik naujiems kontekstams. Įprastas paleidimas neištrina pakeistų duomenų; PostgreSQL juos išlaiko tarp paleidimų.

## Testai

Kitame projekto terminale įkelkite tą pačią konfigūraciją; PostgreSQL turi veikti:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
. .\config\local.ps1
.\mvnw.cmd clean verify
```

`clean verify` vykdo **26 branduolio ir 15 integracinių testų (41 iš viso)**, įskaitant tikrą saugojimą, transakcijos atšaukimą ir klientų izoliaciją. Vien `test` komanda integracinių `*IT` nevykdo. Faktiniai paleidimai, skaičiai ir apribojimai pateikti [audite](docs/AUDITAS.md); praleistas testas nelaikomas sėkmingu.

Ataskaitos: `target/surefire-reports` ir `target/failsafe-reports`. Testai automatiškai įjungia `test` profilį. `TestDatabaseGuard` leidžia tik bazę `dotask_test`. Nenustačius `TEST_DB_*`, jie veikia su H2 atmintyje; tai papildoma alternatyva, **ne PostgreSQL elgesio įrodymas**. Pilnai patikrai naudokite aukščiau pateiktą PostgreSQL konfigūraciją.

## Įrankių versijos

| Įrankis / biblioteka | Naudota versija |
| --- | --- |
| Microsoft OpenJDK | 21.0.13.0.2+1 |
| Maven / Wrapper | 3.9.11 / 3.3.4 |
| Spring Boot / Spring Framework | 3.5.16 / 6.2.19 |
| PostgreSQL / JDBC | 17.11 / 42.7.11 |
| JUnit Jupiter / Surefire ir Failsafe | 5.12.2 / 3.5.6 |
| H2 (tik testams) | 2.3.232 |
| IntelliJ IDEA patikrų aplinkoje | 2026.2.3; ši konkreti IDE versija nebūtina |

Versijos fiksuotos `pom.xml` ir `.mvn/wrapper/maven-wrapper.properties`; bibliotekas valdo Spring Boot priklausomybių rinkinys. Oficialūs šaltiniai: [JDK](https://learn.microsoft.com/en-us/java/openjdk/download), [PostgreSQL](https://www.postgresql.org/download/windows/), [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Maven Wrapper](https://maven.apache.org/tools/wrapper/).

## Etapų riba

Vadovautasi visu I etapo planu ir abiem II užduoties PDF puslapiais. II etapo apimties pastraipa leidžia ribotą prototipą, todėl ankstesni I plano prisijungimo ir Docker žingsniai sąmoningai atidėti. DoTask paskirtis ir planavimo taisyklės išlaikytos.

| II etape įgyvendinta | III etapui / vėliau atidėta |
| --- | --- |
| Planavimas, dalinis rezultatas, įvesties ir rezultato patikra | Formalūs kokybės atributų įrodymai, p95/JMeter |
| Užduočių ir prieinamumo formos, sesijų lentelė | Android, Retrofit, priminimai |
| Dvi demo darbo erdvės, serverio nuosavybės filtrai | Prisijungimas, JWT, narystės ir rolės |
| Atominis grafiko pakeitimas ir testai | Kliento nustatymų redaktorius |
| Maven paleidimas, PostgreSQL, dokumentacija | Docker/Compose ir debesija |

Terminas – **2026-11-16 imtinai**, II etapo vertė – **4/10**. I etapo patvirtinimą studentas patikrina pats. Be pagrindinės funkcijos II etapas vertinamas nuliu; nepaaiškinta dalis neįskaitoma. Už pavėluotą savaitę atimamas 1 balas. Pažymys čia neprognozuojamas.

## Demo kontekstai ir nuosavybė

**Klientas / tenant = darbo erdvė arba organizacija.** Tai nėra naršyklės kortelė, užduotis A/B ar vienas žmogus. Naudotojas – žmogus konkrečioje darbo erdvėje. II etape serveris turi dvi iš anksto nustatytas poras:

| Pavadinimas | `X-Demo-Context` | Darbo erdvės ID | Naudotojo ID | Užduotys | Prieinamumas |
| --- | --- | ---: | ---: | --- | --- |
| Fakultetas | `fakultetas` | 1 | 11 | 101 = A, 102 = B | 1001 = spalio 5 d., 1002 = spalio 6 d. |
| Klubas | `klubas` | 2 | 22 | 201 | 2001 |

Klubo pavyzdys skirtingas: užduotis **201 „Klubo renginio planas“**, 90 min., LOW, terminas 2026-10-06 20:00; prieinamumas **2001**, 2026-10-06 17:00–20:00. Pradinė apskaičiuota sesija – 17:00–18:30, likutis 0. Abiejų kontekstų nustatymai vienodi.

`DemoContextResolver` žinomą pasirinkimą susieja su serverio nuosavybės pora. Be konteksto arba su nežinoma reikšme užklausa atmetama; užklausų be nuosavybės filtro nėra. Įrašas pagal ID ieškomas kartu su darbo erdve ir naudotoju. Svetimo ID skaitymas ar keitimas grąžina 404, neatskleidžiant svetimų duomenų. Klientas negali pasirinkti `tenantId` ar `userId` JSON turinyje.

Tai **DEMO konteksto pasirinkimas, ne saugus prisijungimas**. Bet kas demonstracijoje gali tyčia pasirinkti kitą kontekstą. Tikro žmogaus tapatybę ir teisę į darbo erdvę III etape turės nustatyti autentifikacija ir narystė. Čia tikrinama, kad viename nustatytame kontekste užklausos neperžengtų jo ribų.

## Planavimo taisyklės

- Fiksuoti nustatymai abiem erdvėms: **Europe/Vilnius**, sesija iki **120 min.**, pertrauka bent **15 min.**, bendras dienos darbo limitas **180 min.**
- Numatyta eilė: ankstesnis terminas, aukštesnis prioritetas, mažesnis stabilus ID. `LOW < MEDIUM < HIGH` nustatyta skaitiniu svoriu, ne abėcėle. Eilę perduoda Java `Comparator`.
- Pradžia yra įvestis. Ji apvalinama aukštyn iki 15 min.; tiksli riba nekeičiama. Horizontas: pusiau atviras intervalas **[roundedStart, roundedStart.atZone(Europe/Vilnius).plusDays(horizonDays))**. Tai 1–30 vietinių kalendorinių dienų; ties laikrodžio persukimu diena nebūtinai turi 24 valandas.
- Prieinamumas sujungiamas, apkerpamas pagal pradžią ir horizontą bei skaidomas ties vietiniu vidurnakčiu. Sesijų laikas taip pat yra `[start, end)`. Trukmė skaičiuojama realiai praėjusiomis minutėmis.
- Kiekvienai užduočiai iš naujo ieškoma ankstyviausio laisvo tarpo. Tikrinamos pertraukos iki ankstesnės **ir vėlesnės** sesijos; natūralus tarpas jau yra pertrauka. Dienos limitas bendras visoms to naudotojo užduotims. Pertraukos į darbą neįskaičiuojamos.
- Sesijos trukmė – mažiausia likusio darbo, sesijos maksimumo, dienos likučio ir tinkamo laiko iki termino / tarpo / horizonto ribos reikšmė, apvalinta žemyn iki 15 min. Trumpesnis tarpas nepanaudojamas. Sesija gali baigtis tiksliai ties terminu.
- Atliktos užduotys praleidžiamos. Neatliktai reikia teigiamos 15 min. kartotinės trukmės. Pradelsta užduotis arba terminas ties planavimo pradžia negauna darbo sesijos.
- Vienu skaičiavimu priimama iki 1000 užduočių. Per didelė įvestis atmetama, o ne nukerpama.
- Kiekvienos neatliktos užduoties suplanuotos ir nesuplanuotos minutės lygios pradinei likusiai trukmei. Dalinis rezultatas galioja; priežasčių tekstas nereiškia, kad kitokia eilė nieko nepagerintų.
- Skaičiavimas nemažina `remainingMinutes` ir nekeičia `done`. Pakartotinis skaičiavimas pakeičia senas sesijas, jų nedubliuoja. Visas patikrintas grafikas išsaugomas viena transakcija; klaidos atveju senas grafikas išlieka.

Atliktai užduočiai leidžiama 0 arba ankstesnė neneigiama 15 min. kartotinė trukmė; ji vis tiek visai neįtraukiama į planą ir minučių sumų patikrą. Neatlikta užduotis su 0 atmetama. API laiko reikšmės priimamos vietiniu ISO formatu, pvz., `2026-10-05T18:00`, arba su aiškiu tuo metu Vilniuje galiojančiu poslinkiu, pvz., `2026-10-05T18:00:00+03:00`. Neegzistuojantis vietinis pavasario laikas atmetamas. Dviprasmiškam rudens laikui būtinas aiškus galiojantis poslinkis. API rezultatai pateikiami UTC `Instant` formatu, o puslapis juos rodo Europe/Vilnius. Kompiuterio numatytoji laiko juosta planavimo nenulemia. API priima laiką mikrosekundžių tikslumu (iki 6 reikšminių skaitmenų po sekundės); smulkesnė įvestis atmetama, kad PostgreSQL jos nepastebimai nesuapvalintų.

## Fiksuotas I etapo pavyzdys

Pavyzdžio pradžia **2026-10-05 18:00 Europe/Vilnius**, horizontas **3 dienos**. Spalio 5 ir 6 d. prieinamumas 18:00–21:30. Šių datų nekeiskite į šiandieną: lapkritį jos būtų pradelstos. Rezultatas apskaičiuojamas iš šių duomenų, ne grąžinamas kaip paruoštas atsakymas.

| Užduotis | Liko | Prioritetas | Terminas |
| --- | ---: | --- | --- |
| 101 / A – Java laboratorinis | 240 min. | HIGH | 2026-10-07 21:30 |
| 102 / B – UX ataskaita | 120 min. | MEDIUM | 2026-10-06 21:30 |

| Diena | Užduotis | Sesija | Minutės |
| --- | --- | --- | ---: |
| 2026-10-05 | B / 102 | 18:00–20:00 | 120 |
| 2026-10-05 | A / 101 | 20:15–21:15 | 60 |
| 2026-10-06 | A / 101 | 18:00–20:00 | 120 |
| 2026-10-06 | A / 101 | 20:15–21:15 | 60 |

B pirma dėl ankstesnio termino. A iš viso 240 min., B 120 min.; likutis nulis. Pirmą dieną po B lieka tik 60 dienos darbo minučių, nors prieinamumas tęsiasi iki 21:30.

Demonstracija:

1. Pasirinkite „Fakultetas“ ir atkurkite fiksuotą demo pavyzdį.
2. Nurodykite spalio 5 d. 18:00 pradžią, 3 dienas ir perskaičiuokite. Palyginkite keturias sesijas su lentele.
3. Pašalinkite **tik 1002**, spalio 6 d. prieinamumą. Perskaičiuokite: lieka pirmos dienos B 120 ir A 60 min.; A nesuplanuota **180 min.**, B – 0.
4. Atkurkite pavyzdį. Pabandykite A išsaugoti su `remainingMinutes=-15`. Laukiama 400 validavimo klaida; iš naujo perskaitytas grafikas ir ankstesnė užduoties trukmė turi išlikti.
5. Įrodykite izoliaciją tiesioginėmis API užklausomis žemiau. Paleiskite pilną testų komandą.

Kiti plano testai: vienodi terminai → B 18:00–19:00, A 19:15–20:15; tik A su 18:00–20:00 prieinamumu → **120** nesuplanuotų min.; tik A su 18:00–22:30 ir 180 dienos limitu → **60** nesuplanuotų min. Tai skirtingi atvejai nuo dviejų užduočių demo pakeitimo, kur likutis **180**.

## API ir vykdomos demonstravimo komandos

Visi `/api` adresai reikalauja `X-Demo-Context`. POST/PUT turinys – `application/json`; nežinomi ar pasikartojantys JSON laukai ir papildomas turinys po objekto atmetami. Laukų tipai tikrinami griežtai: skaičius negali pakeisti pavadinimo ar loginės būsenos, o tekstas negali pakeisti minučių skaičiaus. Netinkamas `Content-Type` grąžina 415, nesuderinamas `Accept` – 406 prieš keičiant duomenis. Užduoties kūrimo / keitimo laukai: `title`, `deadline`, `priority`, `remainingMinutes`, `done`. Prieinamumo – `start`, `end`. Naujų įrašų ID priskiria serveris.

| Adresas | Metodai | Paskirtis |
| --- | --- | --- |
| `/api/context` | GET | Aktyvi serverio nuosavybės pora ir fiksuoti nustatymai |
| `/api/tasks` | GET, POST | Konteksto užduotys, naujos užduoties kūrimas |
| `/api/tasks/{id}` | GET, PUT, DELETE | Tik savo užduoties skaitymas, keitimas, trynimas |
| `/api/availability` | GET, POST | Konteksto prieinamumas ir naujo intervalo kūrimas |
| `/api/availability/{id}` | GET, PUT, DELETE | Tik savo intervalo skaitymas, keitimas, trynimas |
| `/api/schedule` | GET | Išsaugotas grafikas ir jo pradžia, horizontas, `stale` žyma |
| `/api/plan` | POST | Apskaičiuoti ir viena transakcija pakeisti grafiką |
| `/api/demo/reset` | POST | Atkurti tik aktyvaus konteksto skirtą demo rinkinį |

Šias komandas vykdykite PowerShell, kai programa paleista numatytu portu. Jos naudoja tikrus fiksuotų demo įrašų ID.

```powershell
$base = 'http://localhost:8080/api'
$faculty = @{ 'X-Demo-Context' = 'fakultetas' }
$club = @{ 'X-Demo-Context' = 'klubas' }
$jsonType = 'application/json; charset=utf-8'

Invoke-RestMethod "$base/demo/reset" -Method Post -Headers $faculty
$request = @{ planningStart='2026-10-05T18:00:00+03:00'; horizonDays=3 } | ConvertTo-Json
$plan = Invoke-RestMethod "$base/plan" -Method Post -Headers $faculty -ContentType $jsonType -Body $request
$plan.sessions | Format-Table
$plan.unscheduled | Format-Table

# Pašalinama tik Fakulteto spalio 6 d. prieinamumo eilutė.
Invoke-RestMethod "$base/availability/1002" -Method Delete -Headers $faculty
$partial = Invoke-RestMethod "$base/plan" -Method Post -Headers $faculty -ContentType $jsonType -Body $request
$partial.unscheduled | Format-Table
```

Pradinio demo atkūrimas ir neteisingas redagavimas:

```powershell
Invoke-RestMethod "$base/demo/reset" -Method Post -Headers $faculty
$before = Invoke-RestMethod "$base/schedule" -Headers $faculty | ConvertTo-Json -Depth 10 -Compress
$invalid = @{ title='Java laboratorinis'; deadline='2026-10-07T21:30:00+03:00'; priority='HIGH'; remainingMinutes=-15; done=$false } | ConvertTo-Json
try {
    Invoke-RestMethod "$base/tasks/101" -Method Put -Headers $faculty -ContentType $jsonType -Body $invalid
} catch {
    [int]$_.Exception.Response.StatusCode  # Laukiama 400
    if ($_.ErrorDetails.Message) {
        $_.ErrorDetails.Message
    } else {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $reader.ReadToEnd()               # Klaidos tekstas Windows PowerShell 5.1
        $reader.Dispose()
    }
}
$after = Invoke-RestMethod "$base/schedule" -Headers $faculty | ConvertTo-Json -Depth 10 -Compress
$before -ceq $after                      # Laukiama True
(Invoke-RestMethod "$base/tasks/101" -Headers $faculty).remainingMinutes  # 240
```

Tikras serverio izoliacijos bandymas: pirmiausia perskaitykite Klubo įrašą jo kontekste, tada Fakulteto kontekste bandykite skaityti, pakeisti ir ištrinti tą patį ID. Kiekviena svetima operacija turi grąžinti 404. Po jų Klubo įrašas turi būti identiškas.

```powershell
$clubBefore = Invoke-RestMethod "$base/tasks/201" -Headers $club | ConvertTo-Json -Compress
$foreignEdit = @{ title='Svetimas pakeitimas'; deadline='2026-10-07T21:30:00+03:00'; priority='HIGH'; remainingMinutes=60; done=$false } | ConvertTo-Json
foreach ($method in @('Get', 'Put', 'Delete')) {
    try {
        if ($method -eq 'Put') {
            Invoke-RestMethod "$base/tasks/201" -Method Put -Headers $faculty -ContentType $jsonType -Body $foreignEdit
        } else {
            Invoke-RestMethod "$base/tasks/201" -Method $method -Headers $faculty
        }
    } catch {
        "$method HTTP $([int]$_.Exception.Response.StatusCode)"  # Kaskart 404
    }
}
$clubAfter = Invoke-RestMethod "$base/tasks/201" -Headers $club | ConvertTo-Json -Compress
$clubBefore -ceq $clubAfter              # Laukiama True
Invoke-RestMethod "$base/tasks" -Headers $faculty | Format-Table
Invoke-RestMethod "$base/tasks" -Headers $club | Format-Table
(Invoke-RestMethod "$base/schedule" -Headers $faculty).sessions | Format-Table
(Invoke-RestMethod "$base/schedule" -Headers $club).sessions | Format-Table
```

Sąrašai ir grafikai privalo turėti tik aktyvaus konteksto užduotis. Trūkstamą / nežinomą kontekstą ir suklastotą `tenantId` turinyje taip pat tikrina integraciniai testai. Testai tikrina ir svetimą prieinamumą bei susijusių užduočių nuosavybę.

## Saugus demo atkūrimas ir saugojimas

`POST /api/demo/reset` ir puslapio atkūrimo mygtukas keičia tik pasirinktą fiksuotą demo rinkinį bei jo grafiką. Jei tame kontekste yra vartotojo papildomai sukurtų užduočių ar prieinamumo, atkūrimas **atsisako su 409**, kad jų neprarastumėte. Tokius įrašus pašalinkite tik patys sąmoningai nusprendę; masinio valymo komandos nėra. Kitas klientas niekada neatkuriamas kartu. Įprastas paleidimas neatkuria demo iš naujo.

Po įvesties pakeitimų senas grafikas gali nebeatitikti naujos įvesties: `stale=true` nurodo, kad reikia perskaičiuoti. Galiojančiai ištrynus užduotį, bazės ryšys pašalina ir **tos užduoties** ankstesnes sesijas bei likutį; kitų užduočių eilutės lieka, bet visas vaizdas pažymimas pasenusiu. Redaguojant užduotį ar keičiant prieinamumą ankstesnės sesijos lieka iki perskaičiavimo. Netinkamas redagavimas nei įvesties, nei ankstesnio grafiko nepakeičia. Naujas rezultatas tikrinamas prieš išsaugojimą; ištrynimas ir naujų sesijų / likučių įterpimas priklauso vienai transakcijai.

## GitHub ir pateikimas

Pradinėje projekto vietoje `.git` ir `remote` nebuvo. Pagal paskutinį naudotojo nurodymą GitHub konfigūravimas ir įkėlimas palikti jam: **NOT PUSHED**, commit nesukurtas. Tai dar nėra pateikta užduotis.

**1. Nukopijuokite savo esamos repozitorijos adresą.** GitHub atverkite **tą pačią I etapo repozitoriją**, spauskite **Code → HTTPS** ir nukopijuokite adresą. Naujos repozitorijos kurti nereikia. Toliau pasirinktas naujas `DoTask` poaplankis esamoje repozitorijoje, kad I etapo dokumentai išliktų savo vietose.

**2. Atsisiųskite repozitorijos kopiją.** Šio projekto IntelliJ terminale, šalia `pom.xml`, vykdykite:

```powershell
git --version
$repoUrl = Read-Host 'Iklijuokite I etapo GitHub repozitorijos HTTPS adresa'
git clone $repoUrl ../DoTask-GitHub
```

Tęskite tik jei klonavimas pavyko. `DoTask-GitHub` bus naujas katalogas šalia dabartinio projekto. Jei toks katalogas jau yra, naudokite savo esamą kloną ir atitinkamai pakeiskite tolesnius kelius. Jei Git paprašo prisijungti, atlikite jo siūlomą prisijungimą savo paskyra; slaptažodžių į projekto failus nerašykite.

**3. Nukopijuokite tik pateikimui reikalingus failus.** Dar būdami dabartinio projekto terminale vykdykite:

```powershell
$repoProject = '../DoTask-GitHub/DoTask'
if (Test-Path -LiteralPath $repoProject) { throw 'DoTask aplankas jau yra: pirma perziurekite jo turini.' }
New-Item -ItemType Directory -Path $repoProject | Out-Null
Copy-Item -LiteralPath 'pom.xml','mvnw','mvnw.cmd','.mvn','.gitignore','.gitattributes','README.md','src','docs','scripts' -Destination $repoProject -Recurse
New-Item -ItemType Directory -Path "$repoProject/config" | Out-Null
Copy-Item -LiteralPath 'config/example.ps1' -Destination "$repoProject/config/example.ps1"
```

Taip perkeliami kodas, testai, Maven Wrapper, instrukcijos ir PNG schema. **`config/local.ps1`, `.local`, `target`, `.idea`, `*.iml` ir duomenų bazės failų nekelkite.** Jie yra vietinė konfigūracija arba generuojami failai. I etapo failai lieka repozitorijos šaknyje; neperrašykite jų nauju README.

**4. Peržiūrėkite ir įkelkite.** Pereikite į klonuotą repozitoriją:

```powershell
Set-Location ../DoTask-GitHub
git remote -v
git branch --show-current
git status --short
git add DoTask
git diff --cached --check
git diff --cached --stat
git diff --cached
```

`origin` turi rodyti jūsų I etapo repozitoriją. Peržiūroje turi būti tik norimi `DoTask` failai ir neturi būti slaptažodžių. Ilgą `git diff` peržiūrą uždarysite klavišu **Q**. Tada:

```powershell
git commit -m "Implement DoTask Part II prototype"
git push
```

`commit` išsaugo pakeitimą vietoje; **tik sėkmingas `push` įkelia jį į GitHub**. Klonuota šaka paprastai jau susieta su nuotoline šaka, todėl nereikia spėti, ar ji vadinasi `main`, ar `master`. Jei push atmetamas dėl kitų naujų pakeitimų, sustokite ir juos suderinkite; nenaudokite `--force`.

Jei pirmas `commit` paprašo nurodyti autorių, šiame klone nustatykite savo vardą ir GitHub el. paštą (galima naudoti GitHub paskyroje nurodytą `noreply` adresą), tada pakartokite `commit` ir `push`:

```powershell
git config user.name (Read-Host 'Jusu Git autoriaus vardas')
git config user.email (Read-Host 'Jusu GitHub el. pastas arba noreply adresas')
```

**5. Patikrinkite GitHub.** Atnaujinkite repozitorijos puslapį ir atverkite `DoTask`: turi būti `pom.xml`, `src/main`, `src/test`, `README.md`, `docs/GYNIMAS.md` ir `docs/SCHEMA.png`. Patikrinkite, ar išliko I etapo dokumentai. Dirbant iš šio klono IntelliJ reikia atverti **`DoTask/pom.xml`**, o paleidimo komandas vykdyti **`DoTask` kataloge**; vietinę konfigūraciją pasiruoškite pagal pirmo paruošimo skyrių.

Oficiali instrukcija: [repozitorijos klonavimas](https://docs.github.com/en/repositories/creating-and-managing-repositories/cloning-a-repository) ir [pakeitimų įkėlimas su git push](https://docs.github.com/en/get-started/using-git/pushing-commits-to-a-remote-repository). Šios komandos pateiktos jums; asistentas jų nevykdė ir nieko į GitHub neįkėlė.

## Prieš gynimą

Trumpa reikalavimų ir įrodymų lentelė (balai yra oficialaus kriterijaus svoris, ne pažadėtas įvertinimas):

| II šaltinio punktas | Failas / metodas / testas | Patikros būsena |
| --- | --- | --- |
| Pagrindinė funkcija, 1,50 | `SchedulingService.calculate`; `baseExampleMatchesAllFourSessionsAndSharedDailyLimit`; HTTP `/api/plan` | Algoritmo testai vykdyti; galutinė visos sistemos suvestinė [audite](docs/AUDITAS.md) |
| Struktūra, 1,00 | `domain`, `application`, `web`, `persistence`; `TaskComparators` | Kodo atsakomybių peržiūra atlikta |
| Testai, 0,50 | `SchedulingServiceTest`, `PlanningApiIT`; `clean verify` | Komandos ir faktiniai skaičiai [audite](docs/AUDITAS.md); praleisti testai neskaičiuojami kaip sėkmė |
| Izoliacija, 0,50 | `PlanningRepository.task`; `foreignTaskCannotBeReadUpdatedOrDeletedThroughHttp` | Įgyvendinta SQL ir HTTP ribose; vykdymo rezultatas [audite](docs/AUDITAS.md) |
| Paleidimas / README, 0,50 | Wrapper, `schema.sql`, `DemoFixtures`, šios komandos | Paruošta; švaraus paruošimo ir demo patikros aprašytos [audite](docs/AUDITAS.md) |
| Ta pati GitHub repozitorija | Studentas patikrina I etapo remote ir įkelia failus | **NOT PUSHED**, palikta studentui jo prašymu |

Pagrindinius failus ir konkrečius testų metodus atsidarykite pagal [gynimo gido žemėlapį](docs/GYNIMAS.md#ką-atidaryti-intellij). Grynas algoritmas yra `src/main/java/lt/dotask/domain/SchedulingService.java`, transakcija – `application/PlanningApplicationService.java`, nuosavybės SQL – `persistence/PlanningRepository.java`; testai yra `src/test/java/lt/dotask`.

- [ ] Pats patikrinau I etapo patvirtinimą.
- [ ] Pagal README paleidau programą ir visą patikrą; suprantu, kurie rezultatai tikrai vykdyti.
- [ ] Galiu parodyti bazinį, dalinį ir klaidingos įvesties scenarijus.
- [ ] Galiu įrodyti svetimo kliento skaitymo ir keitimo atsisakymą per API.
- [ ] Perskaičiau `docs/GYNIMAS.md`, atsidariau tikras klases ir pats pasipraktikavau mažą pakeitimą.
- [ ] Viskas įkelta į tą pačią I etapo GitHub repozitoriją prieš atsiskaitymą.

## AI pagalba ir prototipo ribos

Codex padėjo rengti Java kodą, testus, naršyklės sąsają ir dokumentaciją bei vykdyti patikras. Pasirinktas paprastas vienos Maven programos sprendimas ir per `Comparator` perduodama politika; atsisakyta prisijungimo, Android, Docker ir našumo matavimų plėtimo šiame etape. Rezultatai tikrinami pagal fiksuotus I plano pavyzdžius, saugojimo / izoliacijos bandymus ir kodo peržiūrą. Neteigiama, kad studentas jau peržiūrėjo ar supranta visas sugeneruotas dalis.

Ribos: godusis algoritmas negarantuoja optimalumo; demo pasirinkimas netvirtina tapatybės; nustatymai fiksuoti; datos ribojamos 1900–9998 metais; nėra darbo priklausomybių, pasikartojimų, komandos paskirstymo ar išorinių kalendorių. Sąsaja sąmoningai paprasta. Tikros PostgreSQL ir naršyklės patikros būklę žiūrėkite audito dokumente.
