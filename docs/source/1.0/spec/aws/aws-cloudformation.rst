=========================
AWS CloudFormation traits
=========================

CloudFormation traits are used to describe Smithy resources and their
components so they can be converted to `CloudFormation Resource Schemas`_.

.. _aws-cloudformation-overview:

`CloudFormation Resource Schemas`_ are the standard method of `modeling a
resource provider`_ for use within CloudFormation. Smithy's modeled
:ref:`resources <resource>`, utilizing the traits below, can generate these
schemas. Automatically generating schemas from a service's API lowers the
effort needed to generate and maintain them, reduces the potential for errors
in the translation, and provides a more complete depiction of a resource in its
schema. These schemas can be utilized by the `CloudFormation Command Line
Interface`_ to build, register, and deploy `resource providers`_.

.. contents:: Table of contents
    :depth: 3
    :local:
    :backlinks: none


.. _aws.cloudformation#resource-trait:

-------------------------------------
``aws.cloudformation#resource`` trait
-------------------------------------

Summary
    Indicates that a Smithy resource is a CloudFormation resource.
Trait selector
    ``resource``
Value type
    ``structure``

The ``aws.cloudformation#resource`` trait is a structure that supports the
following members:

.. list-table::
    :header-rows: 1
    :widths:  10 20 70

    * - Property
      - Type
      - Description
    * - name
      - ``string``
      - Provides a custom CloudFormation resource name. This defaults to the
        shape name component of the ``resource`` shape's :ref:`shape
        ID <shape-id>`.
    * - additionalSchemas
      - ``list<shapeId>``
      - A list of additional :ref:`shape IDs <shape-id>` of structures that
        will have their properties added to the CloudFormation resource.
        Members of these structures with the same names MUST resolve to the
        same target. See :ref:`aws-cloudformation-property-deriviation` for
        more information.

The following example defines a simple resource that is also a CloudFormation
resource:

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#resource

        @resource
        resource Foo {
            identifiers: {
                fooId: String,
            },
        }


The following example provides a ``name`` value and one structure shape in the
``additionalSchemas`` list.

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#resource

        @resource(
            name: "Foo",
            additionalSchemas: [AdditionalFooProperties])
        resource FooResource {
            identifiers: {
                fooId: String,
            },
        }

        structure AdditionalFooProperties {
            barProperty: String,
        }


.. _aws-cloudformation-property-deriviation:

Resource properties
===================

Smithy will automatically derive `property`__ information for resources with the
``@aws.cloudformation#resource`` trait applied.

A resource's properties include the resource's identifiers as well as the top
level members of the resource's ``read`` operation output structure, ``put``
operation input structure, ``create`` operation input structure, ``update``
operation input structure, and any structures listed in the ``@resource``
trait's ``additionalSchemas`` property. Members of these structures can be
excluded by applying the :ref:`aws.cloudformation#excludeProperty-trait`.

.. __: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-schema.html#schema-properties-properties

.. important::

    Any members used to derive properties that are defined in more than one of
    the above structures MUST resolve to the same target.

.. seealso::

    Refer to :ref:`property mutability <aws-cloudformation-mutability-derivation>`
    for more information on how the CloudFormation mutability of a property is
    derived.


.. _aws.cloudformation#excludeProperty-trait:

--------------------------------------------
``aws.cloudformation#excludeProperty`` trait
--------------------------------------------

Summary
    Indicates that structure member should not be included as a `property`__ in
    generated CloudFormation resource definitions.
Trait selector
    ``structure > member``

    *Any structure member*
Value type
    Annotation trait
Conflicts with
    :ref:`aws.cloudformation#additionalIdentifier-trait`,
    :ref:`aws.cloudformation#mutability-trait`

.. __: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-schema.html#schema-properties-properties

When :ref:`deriving a resource's properties <aws-cloudformation-property-deriviation>`,
all members of the used structures that have the ``excludeProperty`` trait
applied will not be included.

The following example defines a CloudFormation resource that excludes the
``responseCode`` property:

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#excludeProperty
        use aws.cloudformation#resource

        @resource
        resource Foo {
            identifiers: {
                fooId: String,
            },
            read: GetFoo,
        }

        @readonly
        @http(method: "GET", uri: "/foos/{fooId}", code: 200)
        operation GetFoo {
            input: GetFooRequest,
            output: GetFooResponse,
        }

        structure GetFooRequest {
            @httpLabel
            @required
            fooId: String,
        }

        structure GetFooResponse {
            fooId: String,

            @httpResponseCode
            @excludeProperty
            responseCode: Integer,
        }


.. _aws-cloudformation-mutability-derivation:

-------------------
Property mutability
-------------------

Any property derived for a resource will have its mutability automatically
derived as well. CloudFormation resource properties can have the following
mutabilities:

* **Full** - Properties that can be specified when creating, updating, or
  reading a resource.
* **Create Only** - Properties that can be specified only during resource
  creation and can be returned in a ``read`` or ``list`` request.
* **Read Only** - Properties that can be returned by a ``read`` or ``list``
  request, but cannot be set by the user.
* **Write Only** - Properties that can be specified by the user, but cannot be
  returned by a ``read`` or ``list`` request.
* **Create and Write Only** - Properties that can be specified only during
  resource creation and cannot be returned in a ``read`` or ``list`` request.

Given the following model without mutability traits applied,

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#resource

        @resource
        resource Foo {
            identifiers: {
                fooId: String,
            },
            create: CreateFoo,
            read: GetFoo,
            update: UpdateFoo,
        }

        operation CreateFoo {
            input: CreateFooRequest,
            output: CreateFooResponse,
        }

        structure CreateFooRequest {
            createProperty: ComplexProperty,
            mutableProperty: ComplexProperty,
            writeProperty: ComplexProperty,
            createWriteProperty: ComplexProperty,
        }

        structure CreateFooResponse {
            fooId: String,
        }

        @readonly
        operation GetFoo {
            input: GetFooRequest,
            output: GetFooResponse,
        }

        structure GetFooRequest {
            @required
            fooId: String,
        }

        structure GetFooResponse {
            fooId: String,
            createProperty: ComplexProperty,
            mutableProperty: ComplexProperty,
            readProperty: ComplexProperty,
        }

        @idempotent
        operation UpdateFoo {
            input: UpdateFooRequest,
        }

        structure UpdateFooRequest {
            @required
            fooId: String,

            mutableProperty: ComplexProperty,
            writeProperty: ComplexProperty,
        }

        structure ComplexProperty {
            anotherProperty: String,
        }

The computed resource property mutabilities are:

.. list-table::
    :header-rows: 1
    :widths: 50 50

    * - Name
      - Mutability
    * - ``fooId``
      - Read only
    * - ``createProperty``
      - Create only
    * - ``mutableProperty``
      - Full
    * - ``readProperty``
      - Read only
    * - ``writeProperty``
      - Write only
    * - ``createWriteProperty``
      - Create and write only


.. _aws.cloudformation#mutability-trait:

---------------------------------------
``aws.cloudformation#mutability`` trait
---------------------------------------

Summary
    Indicates that the CloudFormation property generated from this has the
    specified mutability.
Trait selector
    ``structure > member``

    *Any structure member*
Value type
    ``string`` that MUST be set to "full", "create", "create-and-read", "read",
    or "write" to indicate the property's specific mutability.
Conflicts with
    :ref:`aws.cloudformation#excludeProperty-trait`

Members with this trait applied will have their `derived mutability
<aws-cloudformation-mutability-deriviation>`_ overridden. The values of the
mutability trait have the following meanings:

.. list-table::
    :header-rows: 1
    :widths: 20 80

    * - Value
      - Description
    * - ``full``
      - Indicates that the CloudFormation property generated from this member
        does not have any mutability restrictions.
    * - ``create``
      - Indicates that the CloudFormation property generated from this member
        can be specified only during resource creation and cannot returned in a
        ``read`` or ``list`` request. This is a equivalent to create and write
        only CloudFormation mutability.
    * - ``create-and-read``
      - Indicates that the CloudFormation property generated from this member
        can be specified only during resource creation and can be returned in a
        ``read`` or ``list`` request. This is equivalent to create only
        CloudFormation mutability.
    * - ``read``
      - Indicates that the CloudFormation property generated from this member
        can be returned by a ``read`` or ``list`` request, but cannot be set by
        the user. This is equivalent to read only CloudFormation mutability.
    * - ``write``
      - Indicates that the CloudFormation property generated from this member
        can be specified by the user, but cannot be returned by a ``read`` or
        ``list`` request. MUST NOT be set if the member is also marked with the
        :ref:`aws.cloudformation#additionalIdentifier-trait`. This is
        equivalent to write only CloudFormation mutability.


The following example defines a CloudFormation resource that marks the derivable
``tags`` and ``barProperty`` properties as fully mutable:

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#mutability
        use aws.cloudformation#resource

        @resource(additionalSchemas: [FooProperties])
        resource Foo {
            identifiers: {
                fooId: String,
            },
            create: CreateFoo,
        }

        operation CreateFoo {
            input: CreateFooRequest,
            output: CreateFooResponse,
        }

        structure CreateFooRequest {
            @mutability("full")
            tags: TagList,
        }

        structure CreateFooResponse {
            fooId: String,
        }

        structure FooProperties {
            @mutability("full")
            barProperty: String,
        }


The following example defines a CloudFormation resource that marks the derivable
``immutableSetting`` property as create and read only:

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#mutability
        use aws.cloudformation#resource

        @resource(additionalSchemas: [FooProperties])
        resource Foo {
            identifiers: {
                fooId: String,
            },
        }

        structure FooProperties {
            @mutability("create-and-read")
            immutableSetting: Boolean,
        }


The following example defines a CloudFormation resource that marks the derivable
``updatedAt`` and ``createdAt`` properties as read only:

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#mutability
        use aws.cloudformation#resource

        @resource(additionalSchemas: [FooProperties])
        resource Foo {
            identifiers: {
                fooId: String,
            },
            read: GetFoo,
        }

        @readonly
        operation GetFoo {
            input: GetFooRequest,
            output: GetFooResponse,
        }

        structure GetFooRequest {
            @required
            fooId: String
        }

        structure GetFooResponse {
            @mutability("read")
            updatedAt: Timestamp,
        }

        structure FooProperties {
            @mutability("read")
            createdAt: Timestamp,
        }


The following example defines a CloudFormation resource that marks the derivable
``secret`` and ``password`` properties as write only:

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#mutability
        use aws.cloudformation#resource

        @resource(additionalSchemas: [FooProperties])
        resource Foo {
            identifiers: {
                fooId: String,
            },
            create: CreateFoo,
        }

        operation CreateFoo {
            input: CreateFooRequest,
            output: CreateFooResponse,
        }

        structure CreateFooRequest {
            @mutability("write")
            secret: String,
        }

        structure CreateFooResponse {
            fooId: String,
        }

        structure FooProperties {
            @mutability("write")
            password: String,
        }

Given the following model with property mutability traits applied,

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#additionalIdentifier
        use aws.cloudformation#excludeProperty
        use aws.cloudformation#mutability
        use aws.cloudformation#resource

        @resource(additionalSchemas: [FooProperties])
        resource Foo {
            identifiers: {
                fooId: String,
            },
            create: CreateFoo,
            read: GetFoo,
            update: UpdateFoo,
        }

        @http(method: "POST", uri: "/foos", code: 200)
        operation CreateFoo {
            input: CreateFooRequest,
            output: CreateFooResponse,
        }

        structure CreateFooRequest {
            @mutability("full")
            tags: TagList,

            @mutability("write")
            secret: String,

            fooAlias: String,

            createProperty: ComplexProperty,
            mutableProperty: ComplexProperty,
            writeProperty: ComplexProperty,
            createWriteProperty: ComplexProperty,
        }

        structure CreateFooResponse {
            fooId: String,
        }

        @readonly
        @http(method: "GET", uri: "/foos/{fooId}", code: 200)
        operation GetFoo {
            input: GetFooRequest,
            output: GetFooResponse,
        }

        structure GetFooRequest {
            @httpLabel
            @required
            fooId: String,

            @httpQuery("fooAlias")
            @additionalIdentifier
            fooAlias: String,
        }

        structure GetFooResponse {
            fooId: String,

            @httpResponseCode
            @excludeProperty
            responseCode: Integer,

            @mutability("read")
            updatedAt: Timestamp,

            createProperty: ComplexProperty,
            mutableProperty: ComplexProperty,
            readProperty: ComplexProperty,
        }

        @idempotent
        @http(method: "PUT", uri: "/foos/{fooId}", code: 200)
        operation UpdateFoo {
            input: UpdateFooRequest,
        }

        structure UpdateFooRequest {
            @httpLabel
            @required
            fooId: String,

            fooAlias: String,
            mutableProperty: ComplexProperty,
            writeProperty: ComplexProperty,
        }

        structure FooProperties {
            addedProperty: String,

            @mutability("full")
            barProperty: String,

            @mutability("create-and-read")
            immutableSetting: Boolean,

            @mutability("read")
            createdAt: Timestamp,

            @mutability("write")
            password: String,
        }

        structure ComplexProperty {
            anotherProperty: String,
        }

        list TagList {
            member: String
        }

The computed resource property mutabilities are:

.. list-table::
    :header-rows: 1
    :widths: 50 50

    * - Name
      - Mutability
    * - ``addedProperty``
      - Full
    * - ``barProperty``
      - Full
    * - ``createProperty``
      - Create only
    * - ``createWriteProperty``
      - Create and write only
    * - ``createdAt``
      - Read only
    * - ``fooAlias``
      - Full
    * - ``fooId``
      - Read only
    * - ``immutableSetting``
      - Create only
    * - ``mutableProperty``
      - Full
    * - ``password``
      - Write only
    * - ``readProperty``
      - Read only
    * - ``secret``
      - Write only
    * - ``tags``
      - Full
    * - ``updatedAt``
      - Read only
    * - ``writeProperty``
      - Write only


.. _aws.cloudformation#propertyName-trait:

-----------------------------------------
``aws.cloudformation#propertyName`` trait
-----------------------------------------

Summary
    The propertyName trait allows a CloudFormation `resource property`__ name
    to differ from a structure member name used in the model.
Trait selector
    ``structure > member``

    *Any structure member*
Value type
    ``string``

.. __: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-schema.html#schema-properties-properties

Given the following structure definition that is converted to a CloudFormation
resource:

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#propertyName

        structure AdditionalFooProperties {
            bar: String,

            @propertyName("Tags")
            tagList: TagList,
        }

the CloudFormation resource would have the following property names derived
from it:

::

    "bar"
    "Tags"

.. _aws.cloudformation#additionalIdentifier-trait:

-------------------------------------------------
``aws.cloudformation#additionalIdentifier`` trait
-------------------------------------------------

Summary
    Indicates that the CloudFormation property generated from this member is an
    `additional identifier`__ for the resource.
Trait selector
    ``structure > :test(member > string)``

    *Any structure member that targets a string*
Value type
    Annotation trait

.. __: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-schema.html#schema-properties-additionalidentifiers

``additionalIdentifier`` traits are ignored when applied outside of the input
to an operation bound to the ``read`` lifecycle of a resource. The
``additionalIdentifier`` trait MUST NOT be applied to members with the
:ref:`aws.cloudformation#mutability-trait` set to ``write-only``.

The following example defines a CloudFormation resource that has the
``fooAlias`` property as an additional identifier:

.. tabs::

    .. code-tab:: smithy

        namespace smithy.example

        use aws.cloudformation#additionalIdentifier
        use aws.cloudformation#resource

        @resource
        resource Foo {
            identifiers: {
                fooId: String,
            },
            read: GetFoo,
        }

        @readonly
        operation GetFoo {
            input: GetFooRequest,
        }

        structure GetFooRequest {
            @required
            fooId: String,

            @additionalIdentifier
            fooAlias: String,
        }


.. _CloudFormation Resource Schemas: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-schema.html
.. _modeling a resource provider: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-types.html
.. _develop the resource provider: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-develop.html
.. _CloudFormation Command Line Interface: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/what-is-cloudformation-cli.html
.. _resource providers: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-types.html
