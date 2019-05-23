# opendial.mume

1. Installare [TreeTagger](http://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/ "HeidelTime TreeTagger introduction") (vedere cartella opendial-mume/treetagger).
2. Creare le cartelle opendial-mume/opendial-mume/opendial/examples e opendial-mume/opendial-mume/opendial/configs. Mettere nella cartella opendial-mume/opendial-mume/opendial/configs i file estratti dall'archivio allegato alla email.
3. Dalla cartella opendial-mume/opendial-mume/opendial, eseguire code(gradle compile). Questo installa tutte le dipendenza e la prima volta potrebbe volerci un po'.
4. Modificare il file configs/default-config.properties impostando la variabile code(timex.treeTaggerHome) alla cartella dell'installazione di TreeTagger ([forse questo può aiutare](https://github.com/HeidelTime/heideltime/wiki/TreeTaggerWrapper "HeidelTime TreeTagger instructions"))
5. Dalla stessa cartella, lanciare OpenDial con code(./scripts/opendial) (code(./scripts/opendial.bat) su un sistema Windows) con le seguenti opzioni:
    * code(-Dfile.encoding=UTF8) per ridurre i problemi con le lettere accentate. In alcuni casi uno degli strumenti usati ha problemi a riconoscere alcune lettere accentate, che vengono sostituite con un punto interrogativo ('?'); questo ovviamnete a volte crea problemi nell'interpretazionde di alcuni indirizzi o città ed uno dei bug che dovranno essere risolti in un prossimo futuro
    * code(-Dgui=false) per disattivare la GUI (omettere questa opzione per lanciare il programma con una modalità 'di default')
    * code(-Ddomain=.\domains\mumedefault\car-pooling.xml) per caricare il modello dell'agente ibrido presentato all'ultima riunione. code(-Ddomain=.\domains\mume\car-pooling.xml) e code(-Ddomain=.\domains\mumesystemdriven\car-pooling.xml) permettono di lanciare le versioni precedenti dell'agente, rispettivamente user-driven e system-driven; tuttavia, sebbene ad uno stato piuttosto avanzato, lo sviluppo di questi due agenti e stato interrotto, così come il testing e la correzione di errori.
6. Per comodità, se sis esegue l'applicazione su un sistema Windows, sono stati creati tre file .bat che lanciano automaticamente l'appilicazione senza GUI (dalla cartella opendial-mume/opendial-mume/opendial)

I modelli XML sono nella cartella opendial-mume/opendial-mume/opendial/domains/ e sottocartelle, mentre le classi Java sono sotto la cartella opendial-mume/opendial-mume/opendial/src (in particolare, i file relativi agli agenti sviluppati ssono stto src/moduls/mume*).

Per quanto riguarda la possibilità di utilizzare OpenDial come servizio Web o come applicazione per smartphone, la cosa migliore penso sia iniziare a guardare le informazioni sul [sito di OpenDial](http://www.opendial-toolkit.net/ "OpenDial site") e nel [progetto GitHub](https://github.com/plison/opendial "OpenDial GitHub").
