FROM python:3-alpine

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

EXPOSE 8080

ENTRYPOINT ["python3"]

CMD ["-m", "openapi_server"]

COPY requirements.txt /usr/src/app/

# connexion install flask by default, which is not mandatory to run aiohttp server, so we remove it with its direct dependencies
RUN pip3 install --no-cache-dir -r requirements.txt && pip3 uninstall --yes itsdangerous click flask

COPY . /usr/src/app
