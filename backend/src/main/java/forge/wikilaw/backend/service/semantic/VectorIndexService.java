package forge.wikilaw.backend.service.semantic;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VectorIndexService {
    private final VectorStore store;
    private final EmbeddingClient embeddings;
    public VectorIndexService(VectorStore store, EmbeddingClient embeddings) { this.store=store; this.embeddings=embeddings; }

    @Transactional
    public void prepareModel() { store.prepareModel(); }

    /** Lock only a bounded batch. A failed request rolls back, leaving the batch pending for retry. */
    @Transactional
    public int indexBatch() {
        var pending = store.lockPending(8);
        var vectors = embeddings.embed(pending.stream().map(VectorStore.Pending::text).toList(), "passage");
        for (int i=0; i<pending.size(); i++) store.save(pending.get(i), vectors.get(i));
        return pending.size();
    }
}
