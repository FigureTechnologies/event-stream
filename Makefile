.PHONY: build-dist clean clean-test docs run-cli test show-docs

# Make all environment variables available to child processes
.EXPORT_ALL_VARIABLES:

GRADLEW   ?= ./gradlew
NAME      := provenance-event-stream
CLI_BUILD := $(PWD)/cli/build
LIB_BUILD := $(PWD)/lib/build
HTML_DOCS := $(LIB_BUILD)/dokka/html

all: run-local

clean:
	$(GRADLEW) clean

clean-test:
	$(GRADLEW) cleanTest

build-dist:
	$(GRADLEW) installDist

run-cli:  build-dist
	@echo "*** PORT 26657 is expected to be open on localhost! ***"
	AWS_REGION=us-east-1 ENVIRONMENT=local $(CLI_BUILD)/install/$(NAME)/bin/$(NAME) $(ARGS)

test: clean-test
	$(GRADLEW) test -i

docs:
	$(GRADLEW) lib:dokkaHtml

show-docs: docs
	open $(HTML_DOCS)/index.html
