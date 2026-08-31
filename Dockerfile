FROM clojure:temurin-21-tools-deps
WORKDIR /app
COPY . /app
EXPOSE 8080
CMD ["clojure", "-M", "-m", "payment-orchestrator-clj.core"]
