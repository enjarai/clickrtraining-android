use core::str;
use std::{io::Cursor, time::Duration};

use anyhow::Result;
use awc::ws::Frame::Text;
use futures::StreamExt;
use log::{info, warn};
use rodio::Decoder;
use tokio::time::{sleep, timeout};

use crate::{build_room_url, ClientArgs};

pub async fn start(args: ClientArgs) -> Result<()> {
    let client = awc::Client::builder()
        .max_http_version(awc::http::Version::HTTP_11)
        .finish();

    loop {
        match client.ws(build_room_url(args.protocol.as_str(), args.addr.as_str(), args.port, args.id.as_str(), "listen")).connect().await {
            Ok((res, mut ws)) => {
                info!("Connected! HTTP response: {res:?}");

                let (_stream, stream_handle) = rodio::OutputStream::try_default()?;
                let sink = rodio::Sink::try_new(&stream_handle)?;
                sink.set_volume(args.volume);

                loop {
                    match timeout(Duration::from_secs(20), ws.next()).await {
                        Ok(Some(msg)) => {
                            if let Ok(Text(msg)) = msg {
                                match str::from_utf8(&msg) {
                                    Ok("c") => {
                                        info!("Click!");

                                        let (_stream, stream_handle) = rodio::OutputStream::try_default()?;
                                        let source = Decoder::new(Cursor::new(include_bytes!("../static/sound.ogg")))?;

                                        sink.append(source);
                                        sink.sleep_until_end();
                                    },
                                    _ => {
                                        info!("Ba-bump");
                                    },
                                }
                            }
                        },
                        Ok(None) => {
                            warn!("Got disconnected! Attempting to reconnect in 5 seconds.");
                            sleep(Duration::from_secs(5)).await;
                            break;
                        },
                        Err(_) => {
                            warn!("Timed out! Attempting to reconnect in 5 seconds.");
                            sleep(Duration::from_secs(5)).await;
                            break;
                        }
                    }
                }
            },
            Err(e) => {
                warn!("Failed to connect to websocket: {e}");
                sleep(Duration::from_secs(5)).await;
            }
        }
    }
}
