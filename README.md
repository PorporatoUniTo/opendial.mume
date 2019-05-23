# opendial.mume

1. Installare <a href="http://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/">TreeTagger</a> (vedere cartella opendial-mume/treetagger).
2. Dalla cartella opendial-mume/opendial-mume/opendial, eseguire <code>gradle compile</code>. Questo installa tutte le dipendenza e la prima volta potrebbe volerci un po'.
3. Modificare il file configs/default-config.properties impostando la variabile <code>timex.treeTaggerHome</code> alla cartella dell'installazione di TreeTagger (<a href="https://github.com/HeidelTime/heideltime/wiki/TreeTaggerWrapper">forse questo può aiutare</a>)
4a. Dalla stessa cartella, lanciare OpenDial con <code>./scripts/opendial</code> (<code>./scripts/opendial.bat</code> su un sistema Windows) con le seguenti opzioni:
    a. <code>-Dfile.encoding=UTF8</code> per ridurre i problemi con le lettere accentate. In alcuni casi uno degli strumenti usati ha problemi a riconoscere alcune lettere accentate, che vengono sostituite con un punto interrogativo ('?'); questo ovviamnete a volte crea problemi nell'interpretazionde di alcuni indirizzi o città ed uno dei bug che dovranno essere risolti in un prossimo futuro
    b. <code>-Dgui=false</code>code> per disattivare la GUI (omettere questa opzione per lanciare il programma con una modalità 'di default')
    c. <code>-Ddomain=.\domains\mumedefault\car-pooling.xml</code> per caricare il modello dell'agente ibrido presentato all'ultima riunione. <code>-Ddomain=.\domains\mume\car-pooling.xml</code> e <code>-Ddomain=.\domains\mumesystemdriven\car-pooling.xml</code> permettono di lanciare le versioni precedenti dell'agente, rispettivamente user-driven e system-driven; tuttavia, sebbene ad uno stato piuttosto avanzato, lo sviluppo di questi due agenti e stato interrotto, così come il testing e la correzione di errori.
4b. Per comodità, se sis esegue l'applicazione su un sistema Windows, sono stati creati tre file .bat che lanciano automaticamente l'appilicazione senza GUI (dalla cartella opendial-mume/opendial-mume/opendial)

I modelli XML sono nella cartella opendial-mume/opendial-mume/opendial/domains/ e sottocartelle, mentre le classi Java sono sotto la cartella opendial-mume/opendial-mume/opendial/src (in particolare, i file relativi agli agenti sviluppati ssono stto src/moduls/mume*).

Per quanto riguarda la possibilità di utilizzare OpenDial come servizio Web o come applicazione per smartphone, la cosa migliore penso sia iniziare a guardare le informazioni sul <a href="http://www.opendial-toolkit.net/">sito di OpenDial</a> e nel <a href="https://github.com/plison/opendial">progetto GitHub</a>.
