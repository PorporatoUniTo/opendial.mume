# opendial.mume

1. Installare [TreeTagger](http://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/ "HeidelTime TreeTagger introduction") (vedere cartella opendial-mume/treetagger).
2. Creare la cartella opendial-mume/opendial/configs e mettervi i file estratti dall'archivio allegato alla email.
3. Modificare il file configs/default-config.properties impostando la variabile <code>timex.treeTaggerHome</code> alla cartella dell'installazione di TreeTagger ([forse questo può aiutare](https://github.com/HeidelTime/heideltime/wiki/TreeTaggerWrapper "HeidelTime TreeTagger instructions"))
4. Dalla cartella opendial-mume/opendial, eseguire <code>gradle compile</code> (Gradle 4.9). Questo installa tutte le dipendenza e la prima volta potrebbe volerci un po'.
5. Creare la cartella opendial-mume/opendial/examples. In questa cartella verranno creati dei file di log che riportano l'evoluzione di ogni variabile interna al sistema durante ogn dialogo, conservata per un'analisi successiva alla terminazione del dialogo stesso.
6. Dalla stessa cartella, lanciare OpenDial con <code>./scripts/opendial</code> (<code>./scripts/opendial.bat</code> su un sistema Windows) con le seguenti opzioni:
    * <code>-Dfile.encoding=UTF8</code> per ridurre i problemi con le lettere accentate. In alcuni casi uno degli strumenti usati ha problemi a riconoscere alcune lettere accentate, che vengono sostituite con un punto interrogativo ('?'); questo ovviamnete a volte crea problemi nell'interpretazionde di alcuni indirizzi o città ed uno dei bug che dovranno essere risolti in un prossimo futuro
    * <code>-Dgui=false</code> per disattivare la GUI (omettere questa opzione per lanciare il programma con una modalità 'di default')
    * <code>-Ddomain=./domains/mumedefault/car-pooling.xml</code> per caricare il modello dell'agente ibrido presentato all'ultima riunione. <code>-Ddomain=./domains/mume/car-pooling.xml</code> e <code>-Ddomain=./domains/mumesystemdriven/car-pooling.xml</code> permettono di lanciare le versioni precedenti dell'agente, rispettivamente user-driven e system-driven; tuttavia, sebbene ad uno stato piuttosto avanzato, lo sviluppo di questi due agenti e stato interrotto, così come il testing e la correzione di errori.
7. Per comodità, se si esegue l'applicazione su un sistema Windows, sono stati creati tre file .bat che lanciano automaticamente l'appilicazione senza GUI (dalla cartella opendial-mume/opendial)

I modelli XML sono nella cartella opendial-mume/opendial/domains/ e sottocartelle, mentre le classi Java sono sotto la cartella opendial-mume/opendial/src (in particolare, i file relativi agli agenti sviluppati sono stto src/opendial/modules/mume*).

Per quanto riguarda la possibilità di utilizzare OpenDial come servizio Web o come applicazione per smartphone, la cosa migliore penso sia iniziare a guardare le informazioni sul [sito di OpenDial](http://www.opendial-toolkit.net/ "OpenDial site") e nel [progetto GitHub](https://github.com/plison/opendial "OpenDial GitHub"). Nella cartella wbapp c'è un prototipo *MOLTO INSTABILE* di un'applicazione Java Spring:
    * importare la cartella WebApp in Intellij (unica IDE testata) come progetto
    * importare come modulo la cartella opendial-mume/opendial
    * copiare la cartella opendial/configs in WebApp
    * modificare nel grade.build il parametro <code>dir:</code> della dipendenza <code>compile fileTree</code> in modo che punti alla cartella opendial/lib
    * modificare il valore della variabile <code>DOMAIN_PATH</code> nella classe <code>BookingController</code> in modo che punti al file opendial/domains/mumedefault/car-pooling.xml dell'installazione attuale
Il prototipo è molto semplice: la calsse <code>BookingController</code> gestisce nuove frasi dell'utente e le passa ad OpenDial tramite il metodo <code>.addUserInput</code> e legge il risultato dalla variabiel <code>u_m</code>. Non è molto sofisticato e si consiglia di prenderlo solo come esempio.

