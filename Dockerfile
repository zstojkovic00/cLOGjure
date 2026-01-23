FROM clojure:lein
WORKDIR /app
COPY . .
RUN lein deps
ENTRYPOINT ["lein", "run"]