FROM rust:1.86-alpine

WORKDIR /usr/src/myapp
COPY . .

RUN cargo install --path .

CMD ["clickrtraining"]
