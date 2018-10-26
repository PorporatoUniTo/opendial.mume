# opendial.mume

1. Installare <a href="http://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/">TreeTagger</a> (vedere cartella opendial-mume\treetagger).
2. Dalla cartella opendial-mume\opendial-mume\opendial, eseguire <code>gradle compile</code>. Questo installa tutte le dipendenza e la prima volta potrebbe volerci un po'.
3. Modificare il file configs\default-config.properties impostando la variabile <code>timex.treeTaggerHome</code> alla cartella dell'installazione di TreeTagger (<a href="https://github.com/HeidelTime/heideltime/wiki/TreeTaggerWrapper">forse questo può aiutare</a>)
4. Dalla stessa cartella, lanciare OpenDial con <code>scripts\opendial</code>
5. Caricare domains\mume\car-pooling.xml
