alter table knowledge_document
    drop column embedding;

create table knowledge_chunk (
    id uuid primary key,
    content text not null,
    metadata json not null,
    embedding vector(384) not null
);

create unique index uq_knowledge_chunk_stable_id
    on knowledge_chunk ((metadata ->> 'chunkId'));

create index idx_knowledge_chunk_embedding_cosine
    on knowledge_chunk using hnsw (embedding vector_cosine_ops);
