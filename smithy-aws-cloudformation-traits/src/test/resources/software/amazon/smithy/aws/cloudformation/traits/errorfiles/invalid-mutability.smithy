$version: "1.0"

namespace smithy.example

use aws.cloudformation#mutability

structure FooStructure {
    @mutability("undefined")
    member: String
}
