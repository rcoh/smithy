$version: "1.0"

namespace smithy.example

use aws.cloudformation#resource

service AdditionalSchemasConflict {
    version: "2020-07-02",
    resources: [
        AdditionalSchemasConflictResource,
    ],
}

@resource(additionalSchemas: [AdditionalSchemasConflictProperties])
resource AdditionalSchemasConflictResource {
    identifiers: {
        fooId: String,
    },
    create: CreateAdditionalSchemasConflictResource,
}

operation CreateAdditionalSchemasConflictResource {
    input: CreateAdditionalSchemasConflictResourceRequest,
}

structure CreateAdditionalSchemasConflictResourceRequest {
    bar: String,
}

structure AdditionalSchemasConflictProperties {
    bar: Boolean,
}
