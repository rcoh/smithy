$version: "1.0"

namespace smithy.example

use aws.cloudformation#additionalIdentifier
use aws.cloudformation#mutability

structure FooStructure {
    @additionalIdentifier
    @mutability("write")
    member: String
}
