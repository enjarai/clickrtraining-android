FROM rust:1.86

WORKDIR /usr/src/myapp
COPY . .

RUN cargo install --path .

CMD ["clickrtraining"]
