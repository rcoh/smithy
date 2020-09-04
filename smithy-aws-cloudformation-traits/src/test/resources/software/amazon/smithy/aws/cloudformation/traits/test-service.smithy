$version: "1.0"

namespace smithy.example

use aws.cloudformation#resource
use aws.cloudformation#additionalIdentifier
use aws.cloudformation#excludeProperty
use aws.cloudformation#mutability

service TestService {
    version: "2020-07-02",
    resources: [
        FooResource,
        BarResource,
    ],
}

/// The Foo resource is cool.
@resource
resource FooResource {
    identifiers: {
        fooId: FooId,
    },
    create: CreateFooOperation,
    read: GetFooOperation,
    update: UpdateFooOperation,
}

operation CreateFooOperation {
    input: CreateFooRequest,
    output: CreateFooResponse,
}

structure CreateFooRequest {
    @mutability("create")
    fooValidCreateProperty: String,

    fooValidCreateReadProperty: String,

    fooValidFullyMutableProperty: ComplexProperty,
}

structure CreateFooResponse {
    fooId: FooId,
}

@readonly
operation GetFooOperation {
    input: GetFooRequest,
    output: GetFooResponse,
}

structure GetFooRequest {
    @required
    fooId: FooId,
}

structure GetFooResponse {
    fooId: FooId,

    @mutability("read")
    fooValidReadProperty: String,

    fooValidCreateReadProperty: String,

    fooValidFullyMutableProperty: ComplexProperty,
}

operation UpdateFooOperation {
    input: UpdateFooRequest,
    output: UpdateFooResponse,
}

structure UpdateFooRequest {
    @required
    fooId: FooId,

    @mutability("write")
    fooValidWriteProperty: String,

    fooValidFullyMutableProperty: ComplexProperty,
}

structure UpdateFooResponse {
    fooId: FooId,

    fooValidReadProperty: String,

    fooValidFullyMutableProperty: ComplexProperty,
}

/// A Bar resource, not that kind of bar though.
@resource(name: "Bar", additionalSchemas: [ExtraBarRequest])
resource BarResource {
    identifiers: {
        barId: BarId,
    },
    put: PutBarOperation,
    read: GetBarOperation,
    operations: [ExtraBarOperation],
    resources: [BazResource],
}

@idempotent
operation PutBarOperation {
    input: PutBarRequest,
}

structure PutBarRequest {
    @required
    barId: BarId,

    barImplicitFullProperty: String,
}

@readonly
operation GetBarOperation {
    input: GetBarRequest,
    output: GetBarResponse,
}

structure GetBarRequest {
    @required
    barId: BarId,

    @additionalIdentifier
    arn: String,
}

structure GetBarResponse {
    barId: BarId,
    barImplicitReadProperty: String,
    barImplicitFullProperty: String,

    @mutability("full")
    barExplicitMutableProperty: String,
}

operation ExtraBarOperation {
    input: ExtraBarRequest,
}

structure ExtraBarRequest {
    @required
    barId: BarId,

    barValidAdditionalProperty: String,

    @excludeProperty
    barValidExcludedProperty: String,
}

/// This is an herb.
@resource("name": "Basil")
resource BazResource {
    identifiers: {
        barId: BarId,
        bazId: BazId,
    },
    create: CreateBazOperation,
    read: GetBazOperation,
    update: UpdateBazOperation,
}

operation CreateBazOperation {
    input: CreateBazRequest,
    output: CreateBazResponse,
}

structure CreateBazRequest {
    @required
    barId: BarId,

    bazImplicitCreateProperty: String,
    bazImplicitFullyMutableProperty: String,
    bazImplicitWriteProperty: String,
}

structure CreateBazResponse {
    barId: BarId,
    bazId: BazId,
}

@readonly
operation GetBazOperation {
    input: GetBazRequest,
    output: GetBazResponse,
}

structure GetBazRequest {
    @required
    barId: BarId,

    @required
    bazId: BazId,
}

structure GetBazResponse {
    barId: BarId,
    bazId: BazId,
    bazImplicitCreateProperty: String,
    bazImplicitReadProperty: String,
    bazImplicitFullyMutableProperty: String,
}

operation UpdateBazOperation {
    input: UpdateBazRequest,
    output: UpdateBazResponse,
}

structure UpdateBazRequest {
    @required
    barId: BarId,

    @required
    bazId: BazId,

    bazImplicitWriteProperty: String,
    bazImplicitFullyMutableProperty: String,
}

structure UpdateBazResponse {
    barId: BarId,
    bazId: BazId,
    bazImplicitWriteProperty: String,
    bazImplicitFullyMutableProperty: String,
}

string FooId

string BarId

string BazId

structure ComplexProperty {
    property: String,
    another: String,
}
