use std::{collections::HashMap, time::Duration};

use actix_files::Files;
use actix_web::{get, web::{self, Payload}, App, HttpRequest, HttpResponse, HttpServer};
use actix_ws::Session;
use env_logger::Target;
use log::{info, LevelFilter};
use tokio::{spawn, sync::Mutex, time::{sleep, timeout}};
use once_cell::sync::Lazy;

static CLIENTS: Lazy<Mutex<HashMap<String, Vec<Session>>>> = Lazy::new(|| Mutex::new(HashMap::new()));

#[get("/api/{id}/listen")]
async fn listen(req: HttpRequest, stream: Payload, id: web::Path<String>) -> Result<HttpResponse, actix_web::Error> {
    if id.len() > 64 {
        return Ok(HttpResponse::BadRequest().finish())
    }
    
    let (res, session, _stream) = actix_ws::handle(&req, stream)?;

    CLIENTS.lock().await
        .entry(id.to_string())
        .or_default()
        .push(session);

    info!("A client has connected on id {}.", id);

    Ok(res)
}

#[get("/api/{id}/click")]
async fn click(id: web::Path<String>) -> Result<HttpResponse, actix_web::Error> {
    if let Some(sessions) = CLIENTS.lock().await.get_mut(&id.to_string()) {
        send_or_drop(sessions, "c").await;

        Ok(HttpResponse::Ok().finish())
    } else {
        Ok(HttpResponse::NotFound().finish())
    }
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    env_logger::Builder::from_default_env()
        .target(Target::Stdout)
        .filter_level(LevelFilter::Info)
        .init();

    spawn(heartbeat());

    HttpServer::new(|| App::new()
            .service(listen)
            .service(click)
            .service(Files::new("/", "./static").index_file("index.html"))
        )
        .bind(("0.0.0.0", 8098))?
        .run()
        .await
}

async fn heartbeat() {
    loop {
        sleep(Duration::from_secs(10)).await;

        let mut clients = CLIENTS.lock().await;

        for (_, sessions) in clients.iter_mut() {
            send_or_drop(sessions, "h").await;
        }

        clients.retain(|_, v| !v.is_empty());
    }
}

async fn send_or_drop(sessions: &mut Vec<Session>, msg: &str) {
    let mut do_retain = Vec::new();

    for session in sessions.iter_mut() {
        match timeout(Duration::from_millis(500), session.text(msg)).await {
            Ok(Ok(_)) => {
                do_retain.push(true);
            }
            Ok(Err(e)) => {
                info!("A client has disconnected: {e}");
                do_retain.push(false);
            }
            Err(e) => {
                info!("A client has timed out: {e}");
                do_retain.push(false);
            }
        }
    }

    sessions.retain(|_| do_retain.pop().unwrap());
}