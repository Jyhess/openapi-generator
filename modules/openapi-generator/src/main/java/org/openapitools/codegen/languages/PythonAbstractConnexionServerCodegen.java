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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FilenameUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.*;

import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

public class PythonAbstractConnexionServerCodegen extends PythonBaseCodegen {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonAbstractConnexionServerCodegen.class);

    public static final String CONTROLLER_PACKAGE = "controllerPackage";
    public static final String DEFAULT_CONTROLLER = "defaultController";
    public static final String SUPPORT_PYTHON2 = "supportPython2";
    public static final String SERVER_PORT = "serverPort";
    static final String MEDIA_TYPE = "mediaType";

    protected String serverPort = "8080";
    protected String controllerPackage = "controllers";
    protected String defaultController = "default_controller";
    protected boolean fixBodyName;

    public PythonAbstractConnexionServerCodegen(String templateDirectory, boolean fixBodyNameValue) {
        super(templateDirectory, "openapi_server");
        fixBodyName = fixBodyNameValue;
        testPackage = "test";

        // set the output folder here
        outputFolder = "generated-code/connexion";

        apiTemplateFiles.put("controller.mustache", ".py");
        modelTemplateFiles.put("model.mustache", ".py");
        apiTestTemplateFiles().put("controller_test.mustache", ".py");

        /*
         * Template Location.  This is the location which templates will be read from.  The generator
         * will use the resource stream to attempt to read the templates.
         */
        embeddedTemplateDir = templateDir = templateDirectory;

        addOption(CONTROLLER_PACKAGE, "controller package", controllerPackage);
        addOption(DEFAULT_CONTROLLER, "default controller", defaultController);
        addOption(SERVER_PORT, "TCP port to listen to in app.run", serverPort);
        addSwitch(SUPPORT_PYTHON2, "support python2", Boolean.FALSE);
    }

    protected void addSupportingFiles() {
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CONTROLLER_PACKAGE)) {
            this.controllerPackage = additionalProperties.get(CONTROLLER_PACKAGE).toString();
        } else {
            additionalProperties.put(CONTROLLER_PACKAGE, this.controllerPackage);
        }

        if (additionalProperties.containsKey(DEFAULT_CONTROLLER)) {
            this.defaultController = additionalProperties.get(DEFAULT_CONTROLLER).toString();
        } else {
            additionalProperties.put(DEFAULT_CONTROLLER, this.defaultController);
        }

        if (additionalProperties.containsKey(SERVER_PORT)) {
            this.serverPort = additionalProperties.get(SERVER_PORT).toString();
        } else {
            additionalProperties.put(SERVER_PORT, serverPort);
        }

        if (Boolean.TRUE.equals(additionalProperties.get(SUPPORT_PYTHON2))) {
            typeMapping.put("long", "long");
        }

        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("test-requirements.mustache", "", "test-requirements.txt"));
        supportingFiles.add(new SupportingFile("requirements.mustache", "", "requirements.txt"));
        supportingFiles.add(new SupportingFile("__main__.mustache", packageName, "__main__.py"));
        supportingFiles.add(new SupportingFile("util.mustache", packageName, "util.py"));
        supportingFiles.add(new SupportingFile("__init__.mustache", packageName + File.separatorChar + controllerPackage, "__init__.py"));
        supportingFiles.add(new SupportingFile("security_controller_.mustache", packageName + File.separatorChar + controllerPackage, "security_controller_.py"));
        supportingFiles.add(new SupportingFile("__init__model.mustache", packageName + File.separatorChar + modelPackage, "__init__.py"));
        supportingFiles.add(new SupportingFile("base_model_.mustache", packageName + File.separatorChar + modelPackage, "base_model_.py"));
        supportingFiles.add(new SupportingFile("openapi.mustache", packageName + File.separatorChar + "openapi", "openapi.yaml"));
        addSupportingFiles();

        modelPackage = packageName + "." + modelPackage;
        controllerPackage = packageName + "." + controllerPackage;
    }

    @Override
    public String apiPackage() {
        return controllerPackage;
    }

    @Override
    public String toApiFilename(String name) {
        // replace - with _ e.g. created-at => created_at
        name = name.replaceAll("-", "_");

        // e.g. PhoneNumberApi.py => phone_number_controller.py
        return underscore(name) + "_controller";
    }

    /**
     * Configures the type of generator.
     *
     * @return the CodegenType for this generator
     * @see org.openapitools.codegen.CodegenType
     */
    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    /**
     * Returns human-friendly help for the generator.  Provide the consumer with help
     * tips, parameters here
     *
     * @return A string value for the help message
     */
    @Override
    public String getHelp() {
        return "Generates a Python server library using the Connexion project. By default, " +
                "it will also generate service classes -- which you can disable with the `-Dnoservice` environment variable.";
    }

    @Override
    public String toApiName(String name) {
        if (name == null || name.length() == 0) {
            return "DefaultController";
        }
        return camelize(name, false) + "Controller";
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        // need vendor extensions for x-openapi-router-controller
        Map<String, PathItem> paths = openAPI.getPaths();
        if (paths != null) {
            List<String> pathnames = new ArrayList(paths.keySet());
            for (String pathname : pathnames) {
                PathItem path = paths.get(pathname);
                // Fix path parameters to be in snake_case
                if (pathname.contains("{")) {
                    String fixedPath = new String();
                    for (String token: pathname.substring(1).split("/")) {
                        if (token.startsWith("{")) {
                            String snake_case_token = "{" + this.toParamName(token.substring(1, token.length()-1)) + "}";
                            if(token != snake_case_token) {
                                token = snake_case_token;
                            }
                        }
                        fixedPath += "/" + token;
                    }
                    if (!fixedPath.equals(pathname)) {
                        LOGGER.warn("Path '" + pathname + "' is not consistant with Python variable names. It will be replaced by '" + fixedPath + "'");
                        paths.remove(pathname);
                        paths.put(fixedPath, path);
                    }
                }
                Map<HttpMethod, Operation> operationMap = path.readOperationsMap();
                if (operationMap != null) {
                    for (HttpMethod method : operationMap.keySet()) {
                        Operation operation = operationMap.get(method);
                        String tag = "default";
                        if (operation.getTags() != null && operation.getTags().size() > 0) {
                            tag = operation.getTags().get(0);
                        }
                        String operationId = operation.getOperationId();
                        if (operationId == null) {
                            operationId = getOrGenerateOperationId(operation, pathname, method.toString());
                        }
                        operation.setOperationId(toOperationId(operationId));
                        if (operation.getExtensions() == null || operation.getExtensions().get("x-openapi-router-controller") == null) {
                            operation.addExtension(
                                    "x-openapi-router-controller",
                                    controllerPackage + "." + toApiFilename(tag)
                            );
                        }
                        if (operation.getParameters() != null) {
                            for (Parameter parameter: operation.getParameters()) {
                                String swaggerParameterName = parameter.getName();
                                String pythonParameterName = this.toParamName(swaggerParameterName);
                                if (!swaggerParameterName.equals(pythonParameterName)) {
                                    LOGGER.warn("Parameter name '" + swaggerParameterName + "' is not consistant with Python variable names. It will be replaced by '" + pythonParameterName + "'");
                                    parameter.setName(pythonParameterName);
                                }
                                if (swaggerParameterName.isEmpty()) {
                                    LOGGER.error("Missing parameter name in " + pathname + "." + parameter.getIn());
                                }
                            }
                        }
                        RequestBody body = operation.getRequestBody();
                        if (fixBodyName && body != null) {
                            if (body.getExtensions() == null || !body.getExtensions().containsKey("x-body-name")) {
                                String bodyParameterName = "body";
                                if (operation.getExtensions() != null && operation.getExtensions().containsKey("x-codegen-request-body-name")) {
                                    bodyParameterName = (String) operation.getExtensions().get("x-codegen-request-body-name");
                                }
                                else {
                                    // Used by code generator
                                    operation.addExtension("x-codegen-request-body-name", bodyParameterName);
                                }
                                // Used by connexion
                                body.addExtension("x-body-name", bodyParameterName);
                            }
                        }
                    }
                }
            }
            // Sort path names after variable name fix
            List<String> fixedPathnames = new ArrayList(paths.keySet());
            Collections.sort(fixedPathnames);
            for (String pathname: fixedPathnames) {
                PathItem pathItem = paths.remove(pathname);
                paths.put(pathname, pathItem);
            }
        }
        addSecurityExtensions(openAPI);
    }

    private void addSecurityExtension(SecurityScheme securityScheme, String extensionName, String functionName) {
        if (securityScheme.getExtensions() == null || ! securityScheme.getExtensions().containsKey(extensionName)) {
            securityScheme.addExtension(extensionName, functionName);
        }
    }

    private void addSecurityExtensions(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components != null && components.getSecuritySchemes() != null) {
            Map<String, SecurityScheme> securitySchemes = components.getSecuritySchemes();
            for(String securityName: securitySchemes.keySet()) {
                SecurityScheme securityScheme = securitySchemes.get(securityName);
                String baseFunctionName = controllerPackage + ".security_controller_.";
                switch(securityScheme.getType()) {
                    case APIKEY:
                        addSecurityExtension(securityScheme, "x-apikeyInfoFunc", baseFunctionName + "info_from_api_key_" + securityName);
                        break;
                    case HTTP:
                        addSecurityExtension(securityScheme, "x-basicInfoFunc", baseFunctionName + "info_from_basic_auth_" + securityName);
                        break;
                    case OPENIDCONNECT:
                        LOGGER.warn("Security type " + securityScheme.getType().toString() + " is not supported by connextion yet");
                    case OAUTH2:
                        addSecurityExtension(securityScheme, "x-tokenInfoFunc", baseFunctionName + "info_from_token_" + securityName);
                        addSecurityExtension(securityScheme, "x-scopeValidateFunc", baseFunctionName + "validate_scope_" + securityName);
                        break;
                    default:
                        LOGGER.warn("Unknown security type " + securityScheme.getType().toString());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getOperations(Map<String, Object> objs) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Map<String, Object> apiInfo = (Map<String, Object>) objs.get("apiInfo");
        List<Map<String, Object>> apis = (List<Map<String, Object>>) apiInfo.get("apis");
        for (Map<String, Object> api : apis) {
            result.add((Map<String, Object>) api.get("operations"));
        }
        return result;
    }

    private static List<Map<String, Object>> sortOperationsByPath(List<CodegenOperation> ops) {
        Multimap<String, CodegenOperation> opsByPath = ArrayListMultimap.create();

        for (CodegenOperation op : ops) {
            opsByPath.put(op.path, op);
        }

        List<Map<String, Object>> opsByPathList = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Collection<CodegenOperation>> entry : opsByPath.asMap().entrySet()) {
            Map<String, Object> opsByPathEntry = new HashMap<String, Object>();
            opsByPathList.add(opsByPathEntry);
            opsByPathEntry.put("path", entry.getKey());
            opsByPathEntry.put("operation", entry.getValue());
            List<CodegenOperation> operationsForThisPath = Lists.newArrayList(entry.getValue());
            operationsForThisPath.get(operationsForThisPath.size() - 1).hasMore = false;
            if (opsByPathList.size() < opsByPath.asMap().size()) {
                opsByPathEntry.put("hasMore", "true");
            }
        }

        return opsByPathList;
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        generateYAMLSpecFile(objs);

        for (Map<String, Object> operations : getOperations(objs)) {
            @SuppressWarnings("unchecked")
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");

            List<Map<String, Object>> opsByPathList = sortOperationsByPath(ops);
            operations.put("operationsByPath", opsByPathList);
        }
        return super.postProcessSupportingFileData(objs);
    }

    @Override
    public Map<String, Object> postProcessAllModels(Map<String, Object> objs) {
        Map<String, Object> result = super.postProcessAllModels(objs);
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            Map<String, Object> inner = (Map<String, Object>) entry.getValue();
            List<Map<String, Object>> models = (List<Map<String, Object>>) inner.get("models");
            for (Map<String, Object> mo : models) {
                CodegenModel cm = (CodegenModel) mo.get("model");
                // Add additional filename information for imports
                mo.put("pyImports", toPyImports(cm, cm.imports));
            }
        }
        return result;
    }

    private List<Map<String, String>> toPyImports(CodegenModel cm, Set<String> imports) {
        List<Map<String, String>> pyImports = new ArrayList<>();
        for (String im : imports) {
            if (!im.equals(cm.classname)) {
                HashMap<String, String> pyImport = new HashMap<>();
                pyImport.put("import", toModelImport(im));
                pyImports.add(pyImport);
            }
        }
        return pyImports;
    }

    @Override
    public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");

        for (CodegenOperation operation : operationList) {
            Map<String, String> skipTests = new HashMap<>();
            // Set flag to deactivate tests due to connexion issue.
            if (operation.consumes != null ) {
                if (operation.consumes.size() == 1) {
                    Map<String, String> consume = operation.consumes.get(0);
                    if (! "application/json".equals(consume.get(MEDIA_TYPE))) {
                        skipTests.put("reason", consume.get(MEDIA_TYPE) + " not supported by Connexion");
                        if ("multipart/form-data".equals(consume.get(MEDIA_TYPE))) {
                            operation.isMultipart = Boolean.TRUE;
                        }
                    }
                    operation.vendorExtensions.put("x-prefered-consume", consume);
                }
                else if (operation.consumes.size() > 1) {
                    Map<String, String> consume = operation.consumes.get(0);
                    skipTests.put("reason", "Connexion does not support multiple consummes. See https://github.com/zalando/connexion/pull/760");
                    operation.vendorExtensions.put("x-prefered-consume", consume);
                    if ("multipart/form-data".equals(consume.get(MEDIA_TYPE))) {
                        operation.isMultipart = Boolean.TRUE;
                    }
                }
            }
            else {
                // A body without consumes means '*/*' has been used instead of application/json
                if (operation.bodyParam != null) {
                    Map<String, String> consume = new HashMap<>();
                    consume.put(MEDIA_TYPE, "application/json");
                    operation.vendorExtensions.put("x-prefered-consume", consume);
                    skipTests.put("reason", "*/* not supported by Connexion. Use application/json instead. See https://github.com/zalando/connexion/pull/760");
                }
            }
            // Choose to consume 'application/json' if available, else choose the last one.
            if (operation.produces != null ) {
                for (Map<String, String> produce: operation.produces) {
                    operation.vendorExtensions.put("x-prefered-produce", produce);
                    if (produce.get(MEDIA_TYPE).equals("application/json")) {
                        break;
                    }
                }
            }
            if (! skipTests.isEmpty()) {
                operation.vendorExtensions.put("x-skip-test", skipTests);
            }
            if (operation.requestBodyExamples != null) {
                for (Map<String, String> example: operation.requestBodyExamples) {
                    if (example.get("contentType") != null && example.get("contentType").equals("application/json")) {
                        operation.bodyParam.example = example.get("example");
                    }
                }
            }
        }
        return objs;
    }

}
