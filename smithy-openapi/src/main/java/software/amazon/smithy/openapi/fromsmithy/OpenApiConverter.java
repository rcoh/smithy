/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.openapi.fromsmithy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.smithy.jsonschema.JsonSchemaConstants;
import software.amazon.smithy.jsonschema.JsonSchemaConverter;
import software.amazon.smithy.jsonschema.JsonSchemaMapper;
import software.amazon.smithy.jsonschema.Schema;
import software.amazon.smithy.jsonschema.SchemaDocument;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.node.ToNode;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.ExternalDocumentationTrait;
import software.amazon.smithy.model.traits.TitleTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.validation.ValidationUtils;
import software.amazon.smithy.openapi.OpenApiConstants;
import software.amazon.smithy.openapi.OpenApiException;
import software.amazon.smithy.openapi.model.ComponentsObject;
import software.amazon.smithy.openapi.model.ExternalDocumentation;
import software.amazon.smithy.openapi.model.InfoObject;
import software.amazon.smithy.openapi.model.OpenApi;
import software.amazon.smithy.openapi.model.OperationObject;
import software.amazon.smithy.openapi.model.ParameterObject;
import software.amazon.smithy.openapi.model.PathItem;
import software.amazon.smithy.openapi.model.RequestBodyObject;
import software.amazon.smithy.openapi.model.ResponseObject;
import software.amazon.smithy.openapi.model.SecurityScheme;
import software.amazon.smithy.openapi.model.TagObject;
import software.amazon.smithy.utils.MapUtils;
import software.amazon.smithy.utils.OptionalUtils;
import software.amazon.smithy.utils.Tagged;

/**
 * Converts a Smithy model to OpenAPI.
 */
public final class OpenApiConverter {
    private static final Logger LOGGER = Logger.getLogger(OpenApiConverter.class.getName());

    private Map<String, Node> settings = new HashMap<>();
    private ClassLoader classLoader = OpenApiConverter.class.getClassLoader();
    private JsonSchemaConverter jsonSchemaConverter;
    private final List<OpenApiMapper> mappers = new ArrayList<>();

    private OpenApiConverter() {}

    public static OpenApiConverter create() {
        return new OpenApiConverter();
    }

    /**
     * Set the converter used to build Smithy shapes.
     *
     * @param jsonSchemaConverter Shape converter to use.
     * @return Returns the OpenApiConverter.
     */
    public OpenApiConverter jsonSchemaConverter(JsonSchemaConverter jsonSchemaConverter) {
        this.jsonSchemaConverter = jsonSchemaConverter;
        return this;
    }

    /**
     * Adds an {@link OpenApiMapper} to the converter.
     *
     * <p>This method is used to add custom OpenApiMappers to a converter that
     * are not automatically added by {@link Smithy2OpenApiExtension} objects
     * detected through Java SPI.
     *
     * @param mapper Mapper to add.
     * @return Returns the converter.
     */
    public OpenApiConverter addOpenApiMapper(OpenApiMapper mapper) {
        mappers.add(mapper);
        return this;
    }

    /**
     * Puts a setting on the converter.
     *
     * @param setting Setting name to set.
     * @param value Setting value to set.
     * @param <T> value type to set.
     * @return Returns the OpenApiConverter.
     */
    public <T extends ToNode> OpenApiConverter putSetting(String setting, T value) {
        settings.put(setting, value.toNode());
        return this;
    }

    /**
     * Puts a setting on the converter.
     *
     * @param setting Setting name to set.
     * @param value Setting value to set.
     * @return Returns the OpenApiConverter.
     */
    public OpenApiConverter putSetting(String setting, String value) {
        settings.put(setting, Node.from(value));
        return this;
    }

    /**
     * Puts a setting on the converter.
     *
     * @param setting Setting name to set.
     * @param value Setting value to set.
     * @return Returns the OpenApiConverter.
     */
    public OpenApiConverter putSetting(String setting, Number value) {
        settings.put(setting, Node.from(value));
        return this;
    }

    /**
     * Puts a setting on the converter.
     *
     * @param setting Setting name to set.
     * @param value Setting value to set.
     * @return Returns the OpenApiConverter.
     */
    public OpenApiConverter putSetting(String setting, boolean value) {
        settings.put(setting, Node.from(value));
        return this;
    }

    /**
     * Sets a {@link ClassLoader} to use to discover {@link JsonSchemaMapper},
     * {@link OpenApiMapper}, and {@link OpenApiProtocol} service providers
     * through SPI.
     *
     * <p>The {@code OpenApiConverter} will use its own ClassLoader by default.
     *
     * @param classLoader ClassLoader to use.
     * @return Returns the OpenApiConverter.
     */
    public OpenApiConverter classLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    /**
     * Sets the protocol trait to use when converting the model.
     *
     * @param protocolTraitId Protocol to use when converting.
     * @return Returns the OpenApiConverter.
     */
    public OpenApiConverter protocolTraitId(ShapeId protocolTraitId) {
        return putSetting(OpenApiConstants.PROTOCOL, protocolTraitId.toString());
    }

    /**
     * Converts the given service shape to OpenAPI model using the given
     * Smithy model.
     *
     * @param model Smithy model to convert.
     * @param serviceShapeId Service to convert.
     * @return Returns the converted model.
     */
    public OpenApi convert(Model model, ShapeId serviceShapeId) {
        return convertWithEnvironment(createConversionEnvironment(model, serviceShapeId));
    }

    /**
     * Converts the given service shape to a JSON/Node representation of an
     * OpenAPI model using the given Smithy model.
     *
     * <p>The result of this method may differ from the result of calling
     * {@link OpenApi#toNode()} because this method will pass the Node
     * representation of the OpenAPI through the {@link OpenApiMapper#updateNode}
     * method of each registered {@link OpenApiMapper}. This may cause
     * the returned value to no longer be a valid OpenAPI model but still
     * representative of the desired artifact (for example, an OpenAPI model
     * used with Amazon CloudFormation might used intrinsic JSON functions or
     * variable expressions that are replaced when synthesized).
     *
     * @param model Smithy model to convert.
     * @param serviceShapeId Service to convert.
     * @return Returns the converted model.
     */
    public ObjectNode convertToNode(Model model, ShapeId serviceShapeId) {
        ConversionEnvironment<? extends Trait> environment = createConversionEnvironment(model, serviceShapeId);
        OpenApi openApi = convertWithEnvironment(environment);
        ObjectNode node = openApi.toNode().expectObjectNode();
        return environment.mapper.updateNode(environment.context, openApi, node);
    }

    private ConversionEnvironment<? extends Trait> createConversionEnvironment(Model model, ShapeId serviceShapeId) {
        // Discover OpenAPI extensions.
        List<Smithy2OpenApiExtension> extensions = new ArrayList<>();

        for (Smithy2OpenApiExtension extension : ServiceLoader.load(Smithy2OpenApiExtension.class, classLoader)) {
            extensions.add(extension);
            // Add JSON schema mappers from found extensions.
            for (JsonSchemaMapper mapper : extension.getJsonSchemaMappers()) {
                getJsonSchemaConverter().addMapper(mapper);
            }
        }

        // Update the JSON schema config with the settings from this class and
        // configure it to use OpenAPI settings.
        ObjectNode.Builder configBuilder = getJsonSchemaConverter()
                .getConfig()
                .toBuilder()
                .withMember(OpenApiConstants.OPEN_API_MODE, true)
                .withMember(JsonSchemaConstants.DEFINITION_POINTER, OpenApiConstants.SCHEMA_COMPONENTS_POINTER);

        // Find the service shape.
        ServiceShape service = model.getShape(serviceShapeId)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Shape `%s` not found in model", serviceShapeId)))
                .asServiceShape()
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Shape `%s` is not a service shape", serviceShapeId)));

        settings.forEach(configBuilder::withMember);
        ObjectNode config = configBuilder.build();
        Trait protocolTrait = loadOrDeriveProtocolTrait(model, service, config);
        OpenApiProtocol<Trait> openApiProtocol = loadOpenApiProtocol(service, protocolTrait, extensions);

        // Merge in protocol default values.
        config = openApiProtocol.getDefaultSettings().merge(config);

        getJsonSchemaConverter().config(config);
        ComponentsObject.Builder components = ComponentsObject.builder();
        SchemaDocument schemas = addSchemas(components, model, service);

        // Load security scheme converters.
        List<SecuritySchemeConverter<? extends Trait>> securitySchemeConverters = loadSecuritySchemes(
                model, service, extensions);

        Context<Trait> context = new Context<>(
                model, service, getJsonSchemaConverter(),
                openApiProtocol, schemas, securitySchemeConverters);

        return new ConversionEnvironment<>(context, extensions, components, mappers);
    }

    // Gets the protocol configured in `protocol` if set.
    //
    // If not set, defaults to the protocol applied to the service IFF the service
    // defines a single protocol.
    //
    // If the derived protocol trait cannot be found on the service, an exception
    // is thrown.
    private Trait loadOrDeriveProtocolTrait(Model model, ServiceShape service, ObjectNode config) {
        ServiceIndex serviceIndex = model.getKnowledge(ServiceIndex.class);
        Set<ShapeId> serviceProtocols = serviceIndex.getProtocols(service).keySet();
        ShapeId protocolTraitId;

        if (config.getMember(OpenApiConstants.PROTOCOL).isPresent()) {
            protocolTraitId = config.expectStringMember(OpenApiConstants.PROTOCOL).expectShapeId();
        } else if (serviceProtocols.isEmpty()) {
            throw new OpenApiException(String.format(
                    "No Smithy protocol was configured and `%s` does not define any protocols.",
                    service.getId()));
        } else if (serviceProtocols.size() > 1) {
            throw new OpenApiException(String.format(
                    "No Smithy protocol was configured and `%s` defines multiple protocols: %s",
                    service.getId(), serviceProtocols));
        } else {
            protocolTraitId = serviceProtocols.iterator().next();
        }

        return service.findTrait(protocolTraitId).orElseThrow(() -> {
            return new OpenApiException(String.format(
                    "Unable to find protocol `%s` on service `%s`. This service supports the following protocols: %s",
                    protocolTraitId, service.getId(), serviceProtocols));
        });
    }

    private static final class ConversionEnvironment<T extends Trait> {
        private final Context<T> context;
        private final List<Smithy2OpenApiExtension> extensions;
        private final ComponentsObject.Builder components;
        private final OpenApiMapper mapper;

        private ConversionEnvironment(
                Context<T> context,
                List<Smithy2OpenApiExtension> extensions,
                ComponentsObject.Builder components,
                List<OpenApiMapper> mappers
        ) {
            this.context = context;
            this.extensions = extensions;
            this.components = components;
            this.mapper = createMapper(mappers);
        }

        private OpenApiMapper createMapper(List<OpenApiMapper> mappers) {
            return OpenApiMapper.compose(Stream.concat(
                    extensions.stream().flatMap(extension -> extension.getOpenApiMappers().stream()),
                    mappers.stream()
            ).collect(Collectors.toList()));
        }
    }

    private <T extends Trait> OpenApi convertWithEnvironment(ConversionEnvironment<T> environment) {
        ServiceShape service = environment.context.getService();
        Context<T> context = environment.context;
        OpenApiMapper mapper = environment.mapper;
        OpenApiProtocol<T> openApiProtocol = environment.context.getOpenApiProtocol();
        OpenApi.Builder openapi = OpenApi.builder().openapi(OpenApiConstants.VERSION).info(createInfo(service));

        mapper.before(context, openapi);

        // The externalDocumentation trait of the service maps to externalDocs.
        service.getTrait(ExternalDocumentationTrait.class)
                .ifPresent(trait -> openapi.externalDocs(
                        ExternalDocumentation.builder().url(trait.getValue()).build()));

        // Include @tags trait tags that are compatible with OpenAPI settings.
        if (environment.context.getConfig().getBooleanMemberOrDefault(OpenApiConstants.OPEN_API_TAGS)) {
            getSupportedTags(service).forEach(tag -> openapi.addTag(TagObject.builder().name(tag).build()));
        }

        addPaths(context, openapi, openApiProtocol, mapper);
        addSecurityComponents(context, openapi, environment.components, mapper);
        openapi.components(environment.components.build());

        // Add arbitrary extensions if they're configured.
        context.getConfig()
                .getObjectMember(JsonSchemaConstants.SCHEMA_DOCUMENT_EXTENSIONS)
                .ifPresent(openapi::extensions);

        return mapper.after(context, openapi.build());
    }

    private JsonSchemaConverter getJsonSchemaConverter() {
        if (jsonSchemaConverter == null) {
            jsonSchemaConverter = JsonSchemaConverter.create();
        }

        return jsonSchemaConverter;
    }

    // Find the corresponding protocol OpenApiProtocol service provider.
    @SuppressWarnings("unchecked")
    private <T extends Trait> OpenApiProtocol<T> loadOpenApiProtocol(
            ServiceShape service,
            T protocolTrait,
            List<Smithy2OpenApiExtension> extensions
    ) {
        // Collect into a list so that a better error message can be presented if the
        // protocol converter can't be found.
        List<OpenApiProtocol> protocolProviders = extensions.stream()
                .flatMap(e -> e.getProtocols().stream())
                .collect(Collectors.toList());

        return protocolProviders.stream()
                .filter(openApiProtocol -> openApiProtocol.getProtocolType().equals(protocolTrait.getClass()))
                .findFirst()
                .map(result -> (OpenApiProtocol<T>) result)
                .orElseThrow(() -> {
                    Stream<String> supportedProtocols = protocolProviders.stream()
                            .map(OpenApiProtocol::getProtocolType)
                            .map(Class::getCanonicalName);
                    return new OpenApiException(String.format(
                            "Unable to find an OpenAPI service provider for the `%s` protocol when converting `%s`. "
                            + "Protocol service providers were found for the following protocol classes: [%s].",
                            protocolTrait.toShapeId(),
                            service.getId(),
                            ValidationUtils.tickedList(supportedProtocols)));
                });
    }

    // Loads all of the OpenAPI security scheme implementations that are referenced by a service.
    private List<SecuritySchemeConverter<? extends Trait>> loadSecuritySchemes(
            Model model,
            ServiceShape service,
            List<Smithy2OpenApiExtension> extensions
    ) {
        // Note: Using a LinkedHashSet here in case order is ever important.
        ServiceIndex serviceIndex = model.getKnowledge(ServiceIndex.class);
        Set<Class<? extends Trait>> schemes = getTraitMapTypes(serviceIndex.getAuthSchemes(service));

        List<SecuritySchemeConverter<? extends Trait>> converters = extensions.stream()
                .flatMap(extension -> extension.getSecuritySchemeConverters().stream())
                .collect(Collectors.toList());

        List<SecuritySchemeConverter<? extends Trait>> resolved = new ArrayList<>();
        for (SecuritySchemeConverter<? extends Trait> converter : converters) {
            if (schemes.remove(converter.getAuthSchemeType())) {
                resolved.add(converter);
            }
        }

        if (!schemes.isEmpty()) {
            LOGGER.warning(() -> String.format(
                    "Unable to find an OpenAPI authentication converter for the following schemes: [%s]", schemes));
        }

        return resolved;
    }

    // Gets the tags of a shape that are allowed in the OpenAPI model.
    private Stream<String> getSupportedTags(Tagged tagged) {
        ObjectNode config = getJsonSchemaConverter().getConfig();
        List<String> supported = config.getArrayMember(OpenApiConstants.OPEN_API_SUPPORTED_TAGS)
                .map(array -> array.getElementsAs(StringNode::getValue))
                .orElse(null);
        return tagged.getTags().stream().filter(tag -> supported == null || supported.contains(tag));
    }

    private InfoObject createInfo(ServiceShape service) {
        InfoObject.Builder infoBuilder = InfoObject.builder();
        // Service documentation maps to info.description.
        service.getTrait(DocumentationTrait.class).ifPresent(trait -> infoBuilder.description(trait.getValue()));
        // Service version maps to info.version.
        infoBuilder.version(service.getVersion());
        // The title trait maps to info.title.
        infoBuilder.title(service.getTrait(TitleTrait.class)
                                  .map(TitleTrait::getValue)
                                  .orElse(service.getId().getName()));
        return infoBuilder.build();
    }

    // Copies the JSON schema schemas over into the OpenAPI object.
    private SchemaDocument addSchemas(
            ComponentsObject.Builder components,
            Model model,
            ServiceShape service
    ) {
        SchemaDocument document = getJsonSchemaConverter().convert(model, service);
        for (Map.Entry<String, Schema> entry : document.getDefinitions().entrySet()) {
            String key = entry.getKey().replace(OpenApiConstants.SCHEMA_COMPONENTS_POINTER + "/", "");
            components.putSchema(key, entry.getValue());
        }
        return document;
    }

    private <T extends Trait> void addPaths(
            Context<T> context,
            OpenApi.Builder openApiBuilder,
            OpenApiProtocol<T> protocolService,
            OpenApiMapper plugin
    ) {
        TopDownIndex topDownIndex = context.getModel().getKnowledge(TopDownIndex.class);
        Map<String, PathItem.Builder> paths = new HashMap<>();

        // Add each operation connected to the service shape to the OpenAPI model.
        topDownIndex.getContainedOperations(context.getService()).forEach(shape -> {
            OptionalUtils.ifPresentOrElse(protocolService.createOperation(context, shape), result -> {
                PathItem.Builder pathItem = paths.computeIfAbsent(result.getUri(), (uri) -> PathItem.builder());
                // Add security requirements to the operation.
                addOperationSecurity(context, result.getOperation(), shape, plugin);
                // Pass the operation through the plugin system and then build it.
                OperationObject builtOperation = plugin.updateOperation(context, shape, result.getOperation().build());
                // Add tags that are on the operation.
                builtOperation = addOperationTags(context, shape, builtOperation);
                // Update each parameter of the operation and rebuild if necessary.
                builtOperation = updateParameters(context, shape, builtOperation, plugin);
                // Update each response of the operation and rebuild if necessary.
                builtOperation = updateResponses(context, shape, builtOperation, plugin);
                // Update the request body of the operation and rebuild if necessary.
                builtOperation = updateRequestBody(context, shape, builtOperation, plugin);

                switch (result.getMethod().toLowerCase(Locale.US)) {
                    case "get":
                        pathItem.get(builtOperation);
                        break;
                    case "put":
                        pathItem.put(builtOperation);
                        break;
                    case "delete":
                        pathItem.delete(builtOperation);
                        break;
                    case "post":
                        pathItem.post(builtOperation);
                        break;
                    case "patch":
                        pathItem.patch(builtOperation);
                        break;
                    case "head":
                        pathItem.head(builtOperation);
                        break;
                    case "trace":
                        pathItem.trace(builtOperation);
                        break;
                    case "options":
                        pathItem.options(builtOperation);
                        break;
                    default:
                        LOGGER.warning(String.format(
                                "The %s HTTP method of `%s` is not supported by OpenAPI",
                                result.getMethod(), shape.getId()));
                }
            }, () -> LOGGER.warning(String.format(
                    "The `%s` operation is not supported by the `%s` protocol (implemented by `%s`), and "
                    + "was omitted",
                    shape.getId(),
                    protocolService.getClass().getName(),
                    context.getProtocolTrait().toShapeId()))
            );
        });

        for (Map.Entry<String, PathItem.Builder> entry : paths.entrySet()) {
            String pathName = entry.getKey();
            // Enact the plugin infrastructure to update the PathItem if necessary.
            PathItem pathItem = plugin.updatePathItem(context, pathName, entry.getValue().build());
            openApiBuilder.putPath(pathName, pathItem);
        }
    }

    private <T extends Trait> void addOperationSecurity(
            Context<T> context,
            OperationObject.Builder builder,
            OperationShape shape,
            OpenApiMapper plugin
    ) {
        ServiceShape service = context.getService();
        ServiceIndex serviceIndex = context.getModel().getKnowledge(ServiceIndex.class);
        Map<ShapeId, Trait> serviceSchemes = serviceIndex.getEffectiveAuthSchemes(service);
        Map<ShapeId, Trait> operationSchemes = serviceIndex.getEffectiveAuthSchemes(service, shape);

        // Add a security requirement for the operation if it differs from the service.
        if (!operationSchemes.equals(serviceSchemes)) {
            Collection<Class<? extends Trait>> authSchemeClasses = getTraitMapTypes(operationSchemes);
            // Find all the converters with matching types of auth traits on the service.
            Collection<SecuritySchemeConverter<? extends Trait>> converters = findMatchingConverters(
                    context, authSchemeClasses);
            for (SecuritySchemeConverter<? extends Trait> converter : converters) {
                List<String> result = createSecurityRequirements(context, converter, service);
                String openApiAuthName = converter.getAuthSchemeId().toString();
                Map<String, List<String>> authMap = MapUtils.of(openApiAuthName, result);
                Map<String, List<String>> requirement = plugin.updateSecurity(context, shape, converter, authMap);
                if (requirement != null) {
                    builder.addSecurity(requirement);
                }
            }
        }
    }

    // This method exists primarily to appease the type-checker.
    private <P extends Trait, A extends Trait> List<String> createSecurityRequirements(
            Context<P> context,
            SecuritySchemeConverter<A> converter,
            ServiceShape service
    ) {
        return converter.createSecurityRequirements(
                context,
                service.expectTrait(converter.getAuthSchemeType()),
                context.getService());
    }

    private OperationObject addOperationTags(Context context, Shape shape, OperationObject operation) {
        // Include @tags trait tags of the operation that are compatible with OpenAPI settings.
        if (context.getConfig().getBooleanMemberOrDefault(OpenApiConstants.OPEN_API_TAGS)) {
            List<String> tags = getSupportedTags(shape).collect(Collectors.toList());
            if (!tags.isEmpty()) {
                return operation.toBuilder().tags(tags).build();
            }
        }

        return operation;
    }

    // Applies mappers to parameters and updates the operation if parameters change.
    private <T extends Trait> OperationObject updateParameters(
            Context<T> context,
            OperationShape shape,
            OperationObject operation,
            OpenApiMapper plugin
    ) {
        List<ParameterObject> parameters = new ArrayList<>();
        for (ParameterObject parameter : operation.getParameters()) {
            parameters.add(plugin.updateParameter(context, shape, parameter));
        }

        return !parameters.equals(operation.getParameters())
               ? operation.toBuilder().parameters(parameters).build()
               : operation;
    }

    // Applies mappers to each request body and update the operation if the body changes.
    private <T extends Trait> OperationObject updateRequestBody(
            Context<T> context,
            OperationShape shape,
            OperationObject operation,
            OpenApiMapper plugin
    ) {
        return operation.getRequestBody()
                .map(body -> {
                    RequestBodyObject updatedBody = plugin.updateRequestBody(context, shape, body);
                    return body.equals(updatedBody)
                           ? operation
                           : operation.toBuilder().requestBody(updatedBody).build();
                })
                .orElse(operation);
    }

    // Ensures that responses have at least one entry, and applies mappers to
    // responses and updates the operation is a response changes.
    private <T extends Trait> OperationObject updateResponses(
            Context<T> context,
            OperationShape shape,
            OperationObject operation,
            OpenApiMapper plugin
    ) {
        Map<String, ResponseObject> newResponses = new LinkedHashMap<>();

        // OpenAPI requires at least one response, so track the "original"
        // responses vs new/mutated responses.
        Map<String, ResponseObject> originalResponses = operation.getResponses();
        if (operation.getResponses().isEmpty()) {
            String code = context.getOpenApiProtocol().getOperationResponseStatusCode(context, shape);
            originalResponses = MapUtils.of(code, ResponseObject.builder()
                    .description(shape.getId().getName() + " response").build());
        }

        for (Map.Entry<String, ResponseObject> entry : originalResponses.entrySet()) {
            String status = entry.getKey();
            ResponseObject responseObject = plugin.updateResponse(context, status, shape, entry.getValue());
            newResponses.put(status, responseObject);
        }

        if (newResponses.equals(operation.getResponses())) {
            return operation;
        } else {
            return operation.toBuilder().responses(newResponses).build();
        }
    }

    private <T extends Trait> void addSecurityComponents(
            Context<T> context,
            OpenApi.Builder openApiBuilder,
            ComponentsObject.Builder components,
            OpenApiMapper plugin
    ) {
        ServiceShape service = context.getService();
        ServiceIndex serviceIndex = context.getModel().getKnowledge(ServiceIndex.class);

        // Create security components for each referenced security scheme.
        for (SecuritySchemeConverter<? extends Trait> converter : context.getSecuritySchemeConverters()) {
            SecurityScheme createdScheme = createAndUpdateSecurityScheme(context, plugin, converter, service);
            if (createdScheme != null) {
                components.putSecurityScheme(converter.getAuthSchemeId().toString(), createdScheme);
            }
        }

        // Assign the components to the "security" of the service. This is only the
        // auth schemes that apply by default across the entire service.
        Map<ShapeId, Trait> authTraitMap = serviceIndex.getEffectiveAuthSchemes(context.getService());
        Collection<Class<? extends Trait>> defaultAuthTraits = getTraitMapTypes(authTraitMap);

        for (SecuritySchemeConverter<? extends Trait> converter : context.getSecuritySchemeConverters()) {
            if (defaultAuthTraits.contains(converter.getAuthSchemeType())) {
                List<String> result = createSecurityRequirements(context, converter, context.getService());
                String authSchemeName = converter.getAuthSchemeId().toString();
                Map<String, List<String>> requirement = plugin.updateSecurity(
                        context, context.getService(), converter, MapUtils.of(authSchemeName, result));
                if (requirement != null) {
                    openApiBuilder.addSecurity(requirement);
                }
            }
        }
    }

    private Set<Class<? extends Trait>> getTraitMapTypes(Map<ShapeId, Trait> traitMap) {
        return traitMap.values().stream().map(Trait::getClass).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // This method exists primarily to appease the type-checker.
    private <P extends Trait, A extends Trait> SecurityScheme createAndUpdateSecurityScheme(
            Context<P> context,
            OpenApiMapper plugin,
            SecuritySchemeConverter<A> converter,
            ServiceShape service
    ) {
        A authTrait = service.expectTrait(converter.getAuthSchemeType());
        SecurityScheme createdScheme = converter.createSecurityScheme(context, authTrait);
        return plugin.updateSecurityScheme(context, authTrait, createdScheme);
    }

    @SuppressWarnings("unchecked")
    private Collection<SecuritySchemeConverter<? extends Trait>> findMatchingConverters(
            Context<? extends Trait> context,
            Collection<Class<? extends Trait>> schemes
    ) {
        return context.getSecuritySchemeConverters().stream()
                .filter(converter -> schemes.contains(converter.getAuthSchemeType()))
                .map(converter -> (SecuritySchemeConverter<Trait>) converter)
                .collect(Collectors.toList());
    }
}