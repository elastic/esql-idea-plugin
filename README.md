<img alt="Elastic logo" align="right" width="auto" height="auto" src="readme-resources/edti-logo.180x66.png">

# Elasticsearch ES|QL Intellij IDEA Plugin

Experimental plugin enabling autocompletion, syntax check and documentation for [ES|QL](https://www.elastic.co/docs/reference/query-languages/esql) queries in Intellij IDEA for Java and Kotlin. 

## Activation

Comment `// ES|QL` above a text block (triple quotes) and the string will be identified as an ES|QL query.

![Screenshot](/readme-resources/activation.gif)

## Autocompletion

Typing `(ctrl + space)` while writing a query will return a list of acceptable commands/values to continue the query correctly.

![Screenshot](/readme-resources/autocomplete.gif)

## Syntax check

The plugin will highlight errors in queries, explaining what to fix.

![Screenshot](/readme-resources/highlight.gif)

## Documentation

Hovering with the cursor over commands will display documentation describing what the command can be used for and its correct syntax. 

![Screenshot](/readme-resources/docs.gif)

## Configurable with a server instance (preview!)

By using the plugin settings, the plugin can be connected to an Elasticsearch server instance to fetch indices and field names, which will be then added to the autocompletion options. 
Currently, it only works with `FROM`, `SORT`, `EVAL` and `WHERE`.  

![Screenshot](/readme-resources/settings.gif)
