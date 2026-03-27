<img alt="Elastic logo" align="right" width="auto" height="auto" src="readme-resources/edti-logo.180x66.png">

# Elasticsearch ES|QL Intellij IDEA Plugin

Experimental plugin enabling autocompletion, syntax check and documentation for [ES|QL](https://www.elastic.co/docs/reference/query-languages/esql) queries in Intellij IDEA for Java and Kotlin. 

## Activation

Comment `// ES|QL` above a text block (triple quotes) and the string will be identified as an ES|QL query.

![Screenshot](/readme-resources/activation-java.gif)

Same for text files, but you need to separate multiple queries with a semicolon.

![Screenshot](/readme-resources/activation-txt.gif)

## Autocompletion

Autocompletion will trigger automatically while typing, returning a list of acceptable commands/values to continue the query correctly. Alternatively, type `(ctrl + space)` to manually trigger autocompletion. 

![Screenshot](/readme-resources/autocomplete-txt.gif)

If connected to a server instance, the plugin will also fetch indices and field names and add them to the suggestion list where it's possible to use them.

![Screenshot](/readme-resources/autocomplete-java.gif)


## Syntax check

The plugin will highlight errors in queries, explaining what to fix.

![Screenshot](/readme-resources/syntax-check-java.gif)

## Documentation

Hovering with the cursor over commands will display documentation describing what the command can be used for and its correct syntax. 

![Screenshot](/readme-resources/docs-java.gif)

## Configurable with a server instance

By using the settings, the plugin can be connected to an Elasticsearch server instance to fetch indices and field names, which will be then added to the autocompletion options. 

![Screenshot](/readme-resources/connect.gif)

## Run queries

Once connected to an Elasticsearch server instance, queries can be executed by clicking on the green `Run` button. Results will be displayed in the plugin's tool window.

![Screenshot](/readme-resources/query-run-txt.gif)

### Test it locally

By running `./gradlew runIde` (if you want to test .kt files, Kotlin must be configured in the sandbox IDE).
