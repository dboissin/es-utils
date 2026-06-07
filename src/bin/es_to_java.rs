use elasticsearch::{
    auth::Credentials,
    http::transport::{SingleNodeConnectionPool, TransportBuilder},
    indices::IndicesGetMappingParts,
    Elasticsearch,
};
use std::error::Error;
use std::collections::HashSet;
use url::Url;

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 2 || args[1] == "--help" || args[1] == "-h" {
        eprintln!("Usage: es_to_java <index_name> [--package <pkg_name>] [--lists <fields>] [--output <file_path>]");
        eprintln!("\nOptions:");
        eprintln!("  --package <pkg_name>  Java package name (defaults to 'com.example.model')");
        eprintln!("  --lists <fields>      Comma-separated list of field names that should be treated as java.util.List");
        eprintln!("  --output <file_path>  Output path to write the Java class (defaults to stdout)");
        std::process::exit(1);
    }

    let index_name = &args[1];
    let mut package_name = "com.example.model".to_string();
    let mut output_path: Option<String> = None;
    let mut lists_fields = HashSet::new();

    let mut i = 2;
    while i < args.len() {
        match args[i].as_str() {
            "--package" => {
                if i + 1 < args.len() {
                    package_name = args[i + 1].clone();
                    i += 2;
                } else {
                    eprintln!("Error: Missing value for --package");
                    std::process::exit(1);
                }
            }
            "--output" => {
                if i + 1 < args.len() {
                    output_path = Some(args[i + 1].clone());
                    i += 2;
                } else {
                    eprintln!("Error: Missing value for --output");
                    std::process::exit(1);
                }
            }
            "--lists" | "--arrays" => {
                if i + 1 < args.len() {
                    for field in args[i + 1].split(',') {
                        lists_fields.insert(field.trim().to_string());
                    }
                    i += 2;
                } else {
                    eprintln!("Error: Missing value for --lists");
                    std::process::exit(1);
                }
            }
            _ => {
                eprintln!("Error: Unknown argument {}", args[i]);
                std::process::exit(1);
            }
        }
    }

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

    let response = client
        .indices()
        .get_mapping(IndicesGetMappingParts::Index(&[index_name]))
        .send()
        .await?;

    if !response.status_code().is_success() {
        let error_body = response.text().await?;
        eprintln!("Failed to get mapping from Elasticsearch: {}", error_body);
        std::process::exit(1);
    }

    let json: serde_json::Value = response.json().await?;

    let mapping_root = json.get(index_name)
        .or_else(|| json.as_object().and_then(|obj| obj.values().next()))
        .ok_or("No mapping found in response")?;

    let properties = mapping_root
        .get("mappings")
        .and_then(|m| m.get("properties"))
        .and_then(|p| p.as_object())
        .ok_or("No properties found in mapping")?;

    let main_record_name = to_pascal_case(index_name);
    let record_code = generate_record(&main_record_name, properties, &lists_fields, true);

    let full_java_code = format!(
        r#"package {package_name};

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "{index_name}")
{record_code}
"#
    );

    if let Some(path) = output_path {
        std::fs::write(&path, &full_java_code)?;
        println!("Java record successfully generated and saved to {}", path);
    } else {
        println!("{}", full_java_code);
    }

    Ok(())
}

fn to_pascal_case(s: &str) -> String {
    let mut res = String::new();
    let mut capitalize_next = true;
    for c in s.chars() {
        if c == '_' || c == '-' || c == '.' {
            capitalize_next = true;
        } else if capitalize_next {
            res.push(c.to_ascii_uppercase());
            capitalize_next = false;
        } else {
            res.push(c);
        }
    }
    res
}

fn to_camel_case(s: &str) -> String {
    let pascal = to_pascal_case(s);
    if pascal.is_empty() {
        return pascal;
    }
    let mut chars = pascal.chars();
    let first = chars.next().unwrap().to_ascii_lowercase();
    let rest: String = chars.collect();
    format!("{}{}", first, rest)
}

fn escape_java_keyword(name: &str) -> String {
    if name == "class" {
        return "clazz".to_string();
    }
    let keywords = [
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
        "null", "record", "yield", "var",
    ];
    if keywords.contains(&name) {
        format!("{}Field", name)
    } else {
        name.to_string()
    }
}

fn get_field_type_and_annotation(es_type: &str, _field_name: &str) -> (String, String) {
    match es_type {
        "text" => ("String".to_string(), "FieldType.Text".to_string()),
        "keyword" => ("String".to_string(), "FieldType.Keyword".to_string()),
        "long" => ("Long".to_string(), "FieldType.Long".to_string()),
        "integer" => ("Integer".to_string(), "FieldType.Integer".to_string()),
        "short" => ("Short".to_string(), "FieldType.Short".to_string()),
        "byte" => ("Byte".to_string(), "FieldType.Byte".to_string()),
        "double" => ("Double".to_string(), "FieldType.Double".to_string()),
        "float" => ("Float".to_string(), "FieldType.Float".to_string()),
        "boolean" => ("Boolean".to_string(), "FieldType.Boolean".to_string()),
        "date" => ("java.time.Instant".to_string(), "FieldType.Date".to_string()),
        "flattened" => ("java.util.Map<String, String>".to_string(), "FieldType.Flattened".to_string()),
        "dense_vector" => ("java.util.List<Float>".to_string(), "FieldType.Auto".to_string()),
        _ => ("String".to_string(), "FieldType.Auto".to_string()),
    }
}

fn generate_record(
    record_name: &str,
    properties: &serde_json::Map<String, serde_json::Value>,
    lists_fields: &HashSet<String>,
    is_root: bool,
) -> String {
    let mut fields = Vec::new();
    let mut local_nested_records = Vec::new();

    if is_root {
        let mut has_id = false;
        for key in properties.keys() {
            if key == "id" || key == "_id" {
                has_id = true;
                break;
            }
        }
        if !has_id {
            fields.push("    @Id\n    String id".to_string());
        }
    }

    for (field_name, val) in properties {
        let is_id_field = field_name == "id" || field_name == "_id";
        let raw_java_field_name = if is_id_field {
            "id".to_string()
        } else {
            to_camel_case(field_name)
        };
        let java_field_name = escape_java_keyword(&raw_java_field_name);

        if is_id_field {
            fields.push("    @Id\n    String id".to_string());
            continue;
        }

        if let Some(sub_props) = val.get("properties").and_then(|p| p.as_object()) {
            let es_type = val.get("type").and_then(|t| t.as_str()).unwrap_or("object");
            let sub_record_name = to_pascal_case(field_name);
            let sub_record_code = generate_record(&sub_record_name, sub_props, lists_fields, false);
            local_nested_records.push(sub_record_code);

            let is_list = es_type == "nested"
                || lists_fields.contains(field_name)
                || lists_fields.contains(&java_field_name);

            let (java_type, field_type_annotation) = if is_list {
                (format!("java.util.List<{}>", sub_record_name), "FieldType.Nested")
            } else {
                (sub_record_name.clone(), "FieldType.Object")
            };

            fields.push(format!(
                "    @Field(name = \"{}\", type = {})\n    {} {}",
                field_name, field_type_annotation, java_type, java_field_name
            ));
        } else {
            let es_type = val.get("type").and_then(|t| t.as_str()).unwrap_or("keyword");

            if es_type == "object" || es_type == "nested" {
                let is_list = es_type == "nested"
                    || lists_fields.contains(field_name)
                    || lists_fields.contains(&java_field_name);

                let java_type = if is_list {
                    "java.util.List<java.util.Map<String, Object>>".to_string()
                } else {
                    "java.util.Map<String, Object>".to_string()
                };

                let field_type_annotation = if es_type == "nested" {
                    "FieldType.Nested"
                } else {
                    "FieldType.Object"
                };

                fields.push(format!(
                    "    @Field(name = \"{}\", type = {})\n    {} {}",
                    field_name, field_type_annotation, java_type, java_field_name
                ));
            } else {
                let (raw_java_type, field_type_enum) = get_field_type_and_annotation(es_type, field_name);

                let is_list = lists_fields.contains(field_name)
                    || lists_fields.contains(&java_field_name);

                let java_type = if is_list {
                    format!("java.util.List<{}>", raw_java_type)
                } else {
                    raw_java_type
                };

                fields.push(format!(
                    "    @Field(name = \"{}\", type = {})\n    {} {}",
                    field_name, field_type_enum, java_type, java_field_name
                ));
            }
        }
    }

    let mut code = String::new();
    code.push_str(&format!("public record {}(\n", record_name));
    code.push_str(&fields.join(",\n\n"));
    code.push_str("\n) ");

    if local_nested_records.is_empty() {
        code.push_str("{}");
    } else {
        code.push_str("{\n");
        for sub_record in local_nested_records {
            let indented: String = sub_record
                .lines()
                .map(|line| format!("    {}", line))
                .collect::<Vec<String>>()
                .join("\n");
            code.push_str(&indented);
            code.push_str("\n\n");
        }
        code.push_str("}");
    }

    code
}
