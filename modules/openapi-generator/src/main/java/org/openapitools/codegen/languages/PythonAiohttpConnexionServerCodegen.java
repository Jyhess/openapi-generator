/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openapitools.codegen.languages;

import org.openapitools.codegen.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.*;

public class PythonAiohttpConnexionServerCodegen extends PythonAbstractConnexionServerCodegen {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonAiohttpConnexionServerCodegen.class);

    public PythonAiohttpConnexionServerCodegen() {
        super("python-aiohttp", true);
        testPackage = "tests";
        embeddedTemplateDir = templateDir = "python-aiohttp";
    }

    /**
     * Configures a friendly name for the generator.  This will be used by the generator
     * to select the library with the -g flag.
     *
     * @return the friendly name for the generator
     */
    @Override
    public String getName() {
        return "python-aiohttp";
    }

    @Override
    protected void addSupportingFiles() {
        supportingFiles.add(new SupportingFile("conftest.mustache", testPackage, "conftest.py"));
        supportingFiles.add(new SupportingFile("__init__test.mustache", testPackage, "__init__.py"));
    }
}
