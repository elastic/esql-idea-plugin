/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.stream.Collectors

// TODO gradle task to fetch and format docs
// TODO stats-by should be renamed to stats

plugins {
    idea
    java
    id("org.jetbrains.intellij.platform") version "2.6.0"
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

group = "co.elastic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val generatedPath = "src/main/generated"

idea {
    module {
        sourceDirs.add(file(generatedPath))
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1.2")
        bundledPlugins("com.intellij.java", "com.intellij.properties", "org.jetbrains.kotlin")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }

        changeNotes = """
          Initial version
        """
    }
}

dependencies {
    implementation("org.antlr:antlr4:4.13.1")
    testImplementation("junit:junit:4.13.2")
    implementation("co.elastic.clients:elasticsearch-java:9.1.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(project(":antlr"))
}
kotlin {
    jvmToolchain {
        this.languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.register("downloadDocs") {
    doLast {
        val url = "https://www.elastic.co/docs/llm.zip"
        try {
            BufferedInputStream(URL(url).openStream()).use { `in` ->
                FileOutputStream("docs-new").use { fileOutputStream ->
                    val dataBuffer: ByteArray? = ByteArray(1024)
                    var bytesRead: Int
                    while ((`in`.read(dataBuffer, 0, 1024).also { bytesRead = it }) != -1) {
                        fileOutputStream.write(dataBuffer, 0, bytesRead)
                    }
                }
            }
        } catch (e: IOException) {
            throw e
        }
    }
}

tasks.register<Exec>("processDocs") {
    commandLine("sh", "./convert-docs.sh")
}

tasks.register("createJavaFileWithDocs") {
    doLast {
        val tripleQuoteRegex = "\"{3}".toRegex()
        val mapentries = project.fileTree("./coverted-docs")
            .filter { item -> item.toString().endsWith(".html") }
            .map { item ->
                val key = item.nameWithoutExtension
                val content = File(item.toString())
                    .readLines()
                    .stream()
                    // replacing all instances of triple quotes in the docs
                    .map { line -> line.replace(tripleQuoteRegex, "\\\\\"\\\\\"\\\\\"") }
                    // escaping specific patterns containing backslashes
                    .map { line -> line.replace("<code>\\</code>", "<code>\\\\</code>") }
                    .map { line -> line.replace("foo \\* bar", "foo \\\\* bar") }
                    .map { line -> line.replace("foo \\( bar", "foo \\\\( bar") }
                    .collect(Collectors.joining("\\n"))

                val escapedContent = "\"\"\"\n$content\n\"\"\""
                val singleEntry = """
                    Map.entry("$key", $escapedContent)
                """.trimIndent()
                singleEntry
            }
            .stream()
            .collect(Collectors.joining(", "))

        val path = "src/main/java/co/elastic/plugin/documentation/EsqlDocsMap.java"
        val fileContent = """
/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
             
package co.elastic.plugin.documentation;

import com.intellij.platform.backend.documentation.DocumentationResult;

import javax.annotation.Nullable;
import java.util.Map;

//----------------------------------------------------------------
//       THIS CODE IS GENERATED. MANUAL EDITS WILL BE LOST.
//----------------------------------------------------------------

public final class EsqlDocsMap {

    private static Map<String, String> rawDocs = Map.ofEntries(
        $mapentries
    );

    public static @Nullable DocumentationResult getDocforCommand(String command) {
        String content = rawDocs.get(command);
        if (content != null) {
            return DocumentationResult.documentation("<p>" + content + "</p>");
        }
        return null;
    }
}
        """.trimIndent()
        File(path).printWriter().use { out ->
            out.println(fileContent)
        }
    }
}
