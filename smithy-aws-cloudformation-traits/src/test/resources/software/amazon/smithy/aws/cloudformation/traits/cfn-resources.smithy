$version: "1.0"

namespace smithy.example

use aws.cloudformation#resource

@resource
resource FooResource {
    identifiers: {
        fooId: FooId
    }
}

@resource(
    name: "CustomResource",
    additionalSchemas: [ExtraBarRequest]
)
resource BarResource {
    identifiers: {
        barId: BarId
    },
    operations: [ExtraBarOperation],
}

operation ExtraBarOperation {
    input: ExtraBarRequest,
}

structure ExtraBarRequest {
    @required
    barId: BarId,
}

string FooId

string BarId
