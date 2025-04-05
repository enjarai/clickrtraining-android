use anyhow::{anyhow, Result};
use clap::{arg, command, Parser, Subcommand};
use env_logger::Target;
use log::LevelFilter;

mod host;
mod client;

#[derive(Debug, Parser)]
#[command(name = "clickrtraining")]
#[command(about = "Host and client for clickrtraining", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Subcommand)]
enum Command {
    Host(ServerArgs),
    Listen(ClientArgs),
    Click(ClickArgs),
}

#[derive(clap::Args, Debug, Clone)]
#[command(about = "Host a clickrtraining instance", long_about = None)]
struct ServerArgs {
    #[arg(short, long, help = "The address to listen on")]
    addr: String,
    #[arg(short, long, default_value_t = 8098, help = "The port to listen on")]
    port: u16,
}

#[derive(clap::Args, Debug, Clone)]
#[command(about = "Listen for clicks in a room", long_about = None)]
struct ClientArgs {
    #[arg(short, long, help = "The host address")]
    addr: String,
    #[arg(short, long, default_value_t = 8098, help = "The host port")]
    port: u16,
    #[arg(short, long, help = "The room identifier")]
    id: String,
    #[arg(short, long, default_value_t = 1.0, help = "The volume at which to play the clicks")]
    volume: f32,
}

#[derive(clap::Args, Debug, Clone)]
#[command(about = "Click a room", long_about = None)]
struct ClickArgs {
    #[arg(short, long, help = "The host address")]
    addr: String,
    #[arg(short, long, default_value_t = 8098, help = "The host port")]
    port: u16,
    #[arg(short, long, help = "The room identifier")]
    id: String,
}

#[actix_web::main]
async fn main() -> Result<()> {
    env_logger::Builder::from_default_env()
        .target(Target::Stdout)
        .filter_level(LevelFilter::Info)
        .init();

    match Cli::parse().command {
        Command::Host(args) => host::start(args).await,
        Command::Listen(args) => client::start(args).await,
        Command::Click(args) => {
            let client = awc::Client::default();
            client.get(format!("http://{}:{}/api/{}/click", args.addr, args.port, args.id))
                .send()
                .await
                .map(|_e| ())
                .map_err(|e| anyhow!("Failed to ping room: {}", e))
        },
    }
}
