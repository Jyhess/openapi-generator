/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
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

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

public class PythonClientCodegen extends PythonBaseCodegen {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonClientCodegen.class);

    public static final String DEFAULT_LIBRARY = "urllib3";

    protected String apiDocPath = "docs/";
    protected String modelDocPath = "docs/";

    public PythonClientCodegen() {
        super("python", "openapi_client");

        // clear import mapping (from default generator) as python does not use it
        // at the moment
        importMapping.clear();

        supportsInheritance = true;
        apiPackage = "api";
        outputFolder = "generated-code" + File.separatorChar + "python";

        modelTemplateFiles.put("model.mustache", ".py");
        apiTemplateFiles.put("api.mustache", ".py");

        modelTestTemplateFiles.put("model_test.mustache", ".py");
        apiTestTemplateFiles.put("api_test.mustache", ".py");
        modelDocTemplateFiles.put("model_doc.mustache", ".md");
        apiDocTemplateFiles.put("api_doc.mustache", ".md");

        testFolder = "test";

        addSwitch(CodegenConstants.SOURCECODEONLY_GENERATION, CodegenConstants.SOURCECODEONLY_GENERATION_DESC, Boolean.FALSE);
        addSwitch(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG, CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG_DESC, Boolean.TRUE);
        addSwitch(CodegenConstants.HIDE_GENERATION_TIMESTAMP, CodegenConstants.HIDE_GENERATION_TIMESTAMP_DESC, Boolean.TRUE);

        supportedLibraries.put("urllib3", "urllib3-based client");
        supportedLibraries.put("asyncio", "Asyncio-based client (python 3.5+)");
        supportedLibraries.put("tornado", "tornado-based client");
        CliOption libraryOption = new CliOption(CodegenConstants.LIBRARY, "library template (sub-template) to use: asyncio, tornado, urllib3");
        libraryOption.setDefault(DEFAULT_LIBRARY);
        cliOptions.add(libraryOption);
        setLibrary(DEFAULT_LIBRARY);
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.SOURCECODEONLY_GENERATION)) {
            generateSourceCodeOnly = true;
        }

        if (generateSourceCodeOnly) {
            // tests in <package>/test
            testFolder = packageName + File.separatorChar + testFolder;
            // api/model docs in <package>/docs
            apiDocPath = packageName + File.separatorChar + apiDocPath;
            modelDocPath = packageName + File.separatorChar + modelDocPath;
        }
        // make api and model doc path available in mustache template
        additionalProperties.put("apiDocPath", apiDocPath);
        additionalProperties.put("modelDocPath", modelDocPath);

        String readmePath = "README.md";
        String readmeTemplate = "README.mustache";
        if (generateSourceCodeOnly) {
            readmePath = packageName + "_" + readmePath;
            readmeTemplate = "README_onlypackage.mustache";
        }
        supportingFiles.add(new SupportingFile(readmeTemplate, "", readmePath));

        if (!generateSourceCodeOnly) {
            supportingFiles.add(new SupportingFile("tox.mustache", "", "tox.ini"));
            supportingFiles.add(new SupportingFile("test-requirements.mustache", "", "test-requirements.txt"));
            supportingFiles.add(new SupportingFile("requirements.mustache", "", "requirements.txt"));

            supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
            supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));
            supportingFiles.add(new SupportingFile("travis.mustache", "", ".travis.yml"));
            supportingFiles.add(new SupportingFile("setup.mustache", "", "setup.py"));
        }
        supportingFiles.add(new SupportingFile("configuration.mustache", packageName, "configuration.py"));
        supportingFiles.add(new SupportingFile("__init__package.mustache", packageName, "__init__.py"));
        supportingFiles.add(new SupportingFile("__init__model.mustache", packageName + File.separatorChar + modelPackage, "__init__.py"));
        supportingFiles.add(new SupportingFile("__init__api.mustache", packageName + File.separatorChar + apiPackage, "__init__.py"));

        if (Boolean.FALSE.equals(excludeTests)) {
            supportingFiles.add(new SupportingFile("__init__test.mustache", testFolder, "__init__.py"));
        }

        supportingFiles.add(new SupportingFile("api_client.mustache", packageName, "api_client.py"));

        if ("asyncio".equals(getLibrary())) {
            supportingFiles.add(new SupportingFile("asyncio/rest.mustache", packageName, "rest.py"));
            additionalProperties.put("asyncio", "true");
        } else if ("tornado".equals(getLibrary())) {
            supportingFiles.add(new SupportingFile("tornado/rest.mustache", packageName, "rest.py"));
            additionalProperties.put("tornado", "true");
        } else {
            supportingFiles.add(new SupportingFile("rest.mustache", packageName, "rest.py"));
        }

        modelPackage = packageName + "." + modelPackage;
        apiPackage = packageName + "." + apiPackage;
        testPackage = testFolder;
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "python";
    }

    @Override
    public String getHelp() {
        return "Generates a Python client library.";
    }

    @Override
    public String apiDocFileFolder() {
        return (outputFolder + "/" + apiDocPath);
    }

    @Override
    public String modelDocFileFolder() {
        return (outputFolder + "/" + modelDocPath);
    }

    @Override
    public String toModelDocFilename(String name) {
        return toModelName(name);
    }

    @Override
    public String toApiDocFilename(String name) {
        return toApiName(name);
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultApi";
        }
        // e.g. phone_number_api => PhoneNumberApi
        return camelize(name) + "Api";
    }

    @Override
    public String toApiVarName(String name) {
        if (name.length() == 0) {
            return "default_api";
        }
        return underscore(name) + "_api";
    }

}
