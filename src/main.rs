use elasticsearch::{
    auth::Credentials,
    http::transport::{SingleNodeConnectionPool, TransportBuilder},
    Elasticsearch, SearchParts,
};
use json_patch::diff;
use serde_json::{json, Value};
use std::error::Error;
use url::Url;

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let username = std::env::var("ES_USERNAME").unwrap_or_else(|_| "elastic".to_string());
    let password = std::env::var("ES_PASSWORD").unwrap_or_else(|_| "changeme".to_string());
    let es_url = std::env::var("ES_URL").unwrap_or_else(|_| "http://localhost:9200".to_string());

    let credentials = Credentials::Basic(username, password);
    let url = Url::parse(&es_url)?;
    let conn_pool = SingleNodeConnectionPool::new(url);
    let transport = TransportBuilder::new(conn_pool)
        .auth(credentials)
        .build()?;
    let client = Elasticsearch::new(transport);

    let index_a = "mon_index_a";
    let index_b = "mon_index_b";

    println!("--- Début du diff streaming entre {} et {} ---", index_a, index_b);

    // Initialisation des deux curseurs
    let mut cursor_a = EsCursor::new(&client, index_a).await?;
    let mut cursor_b = EsCursor::new(&client, index_b).await?;

    let mut diff_count = 0;

    loop {
        match (cursor_a.current_doc(), cursor_b.current_doc()) {
            (Some((id_a, doc_a)), Some((id_b, doc_b))) => {
                if id_a == id_b {
                    // Les ID correspondent, on valide le contenu
                    if doc_a != doc_b {
                        diff_count += 1;
                        let patch = diff(doc_a, doc_b);
                        println!("\n[MODIFIÉ] ID: {}", id_a);
                        println!("{}", serde_json::to_string_pretty(&patch)?);
                    }
                    cursor_a.next().await?;
                    cursor_b.next().await?;
                } else if id_a < id_b {
                    // id_a n'existe pas dans B (car B est déjà plus loin dans l'ordre alphabétique)
                    diff_count += 1;
                    println!("\n[SUPPRIMÉ dans Index B] ID: {}", id_a);
                    cursor_a.next().await?;
                } else {
                    // id_b n'existe pas dans A
                    diff_count += 1;
                    println!("\n[AJOUTÉ dans Index B] ID: {}", id_b);
                    cursor_b.next().await?;
                }
            }
            (Some((id_a, _)), None) => {
                // L'index B est épuisé, tout le reste de A est considéré comme supprimé
                diff_count += 1;
                println!("\n[SUPPRIMÉ dans Index B] ID: {}", id_a);
                cursor_a.next().await?;
            }
            (None, Some((id_b, _))) => {
                // L'index A est épuisé, tout le reste de B est considéré comme ajouté
                diff_count += 1;
                println!("\n[AJOUTÉ dans Index B] ID: {}", id_b);
                cursor_b.next().await?;
            }
            (None, None) => {
                // Fin de l'analyse des deux index
                break;
            }
        }
    }

    println!("\n--- Fin du rapport. Total de différences : {} ---", diff_count);
    Ok(())
}

/// Structure représentant un curseur de streaming (Search After) sur un index ES
struct EsCursor<'a> {
    client: &'a Elasticsearch,
    index: String,
    buffer: Vec<(String, Value)>,
    search_after: Option<Value>,
    is_exhausted: bool,
    batch_size: usize,
}

impl<'a> EsCursor<'a> {
    async fn new(client: &'a Elasticsearch, index: &str) -> Result<EsCursor<'a>, Box<dyn Error>> {
        let mut cursor = EsCursor {
            client,
            index: index.to_string(),
            buffer: Vec::new(),
            search_after: None,
            is_exhausted: false,
            batch_size: 1000,
        };
        cursor.fetch_next_batch().await?;
        Ok(cursor)
    }

    /// Retourne la référence du document actuellement pointé par le curseur
    fn current_doc(&self) -> Option<&(String, Value)> {
        self.buffer.first()
    }

    /// Avance le curseur au document suivant. Charge un batch si le buffer est vide.
    async fn next(&mut self) -> Result<(), Box<dyn Error>> {
        if !self.buffer.is_empty() {
            self.buffer.remove(0);
        }

        if self.buffer.is_empty() && !self.is_exhausted {
            self.fetch_next_batch().await?;
        }
        Ok(())
    }

    /// Remplit le buffer interne en interrogeant Elasticsearch
    async fn fetch_next_batch(&mut self) -> Result<(), Box<dyn Error>> {
        let mut body = json!({
            "size": self.batch_size,
            "query": { "match_all": {} },
            "sort": [{ "_id": "asc" }]
        });

        if let Some(ref after) = self.search_after {
            body["search_after"] = json!([after]);
        }

        let response = self
            .client
            .search(SearchParts::Index(&[&self.index]))
            .body(body)
            .send()
            .await?;

        let response_body: Value = response.json().await?;
        let hits = response_body["hits"]["hits"]
            .as_array()
            .ok_or("Structure Elasticsearch inattendue")?;

        if hits.is_empty() {
            self.is_exhausted = true;
            return Ok(());
        }

        for hit in hits {
            if let (Some(id), Some(source)) = (hit["_id"].as_str(), hit["_source"].as_object()) {
                self.buffer.push((id.to_string(), Value::Object(source.clone())));
            }
        }

        // Sauvegarde de la valeur de tri du dernier élément pour le search_after suivant
        if let Some(last_hit) = hits.last() {
            self.search_after = last_hit["sort"][0].clone().into();
        }

        if hits.len() < self.batch_size {
            self.is_exhausted = true;
        }

        Ok(())
    }
}
