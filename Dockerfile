FROM rust:1.86

WORKDIR /app
COPY . .

RUN apt update
RUN apt install -y libasound2-dev

RUN cargo install --path .

CMD ["clickrtraining", "host", "--addr", "0.0.0.0", "--port", "8098"]
