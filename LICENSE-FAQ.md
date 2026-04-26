# License FAQ

This FAQ is a plain-English explanation of the repo license.

The root `LICENSE` file controls if there is any conflict.

This FAQ is not legal advice.

## Is This Project Open Source?

No.

Alec's Telemetry is source-available under the Alec's Telemetry Runtime License.
You can read the source, use the runtime in Hytale projects, and ship unmodified
copies with your mod or modpack, but the license does not allow modified
redistribution or general-purpose reuse.

## Can I Use It In My Mod?

Yes.

You may use Alec's Telemetry in a Hytale mod, modpack, server, development tool,
or similar Hytale project.

## Can I Use It In A Paid Mod, Paid Modpack, Or Monetized Server?

Yes, as long as you are using Alec's Telemetry only as telemetry runtime for your
own Hytale project and you follow the license.

Paid or commercial Hytale projects are allowed. Hosted telemetry services,
white-label telemetry services, competing dashboards, and resale of Alec's
Telemetry are not allowed without separate written permission.

## Can I Add Alec's Telemetry As A Dependency?

Yes.

Dependency mode is the recommended default. You may require users to install an
unmodified copy of Alec's Telemetry alongside your mod.

## Can I Bundle Alec's Telemetry Inside My Mod?

Yes, if you bundle an unmodified copy and use the documented embedded-mode
integration points.

You may configure and call Alec's Telemetry through its documented descriptors,
settings, and public APIs. You may not edit its source, rebrand it, or publish a
modified embedded copy.

## Can I Modify It For My Own Mod?

Not under the public license.

The intent is to let modders integrate Alec's Telemetry freely, not to create a
network of modified runtime forks. If you need changes, open an issue, submit a
pull request for upstream inclusion, or ask for separate permission.

## Can I Fork The Repo?

The code host may technically let you fork the repo, but the license does not
give you permission to use, modify, or distribute a fork except where the root
`LICENSE` explicitly allows it.

## Can I Redistribute It?

You may redistribute unmodified copies only when they are bundled with or
required by your Hytale project.

You may not publish Alec's Telemetry as a standalone mirror, alternate download,
renamed package, modified package, SDK, toolkit, or fork.

## Can I Use A Custom Endpoint?

Yes, for your own Hytale project.

The runtime supports custom endpoints so modders and server operators can send
their own telemetry somewhere other than Alec's hosted service. That does not
grant permission to run a hosted or managed telemetry service for third parties.

## Can I Run My Own Public Telemetry Platform With This?

No.

Operating a hosted telemetry service, managed crash-reporting service, dashboard,
alerting product, white-label platform, or competing telemetry SaaS requires
separate written permission.

## Can I Use The Code As Inspiration?

You can read the source and learn from it. Do not copy protected code into a
different project unless the license allows that use or you have separate
permission.

## Can I Use Alec's Branding, Name, Or Logo?

Only for accurate attribution, such as saying your mod uses Alec's Telemetry.

Do not imply that your project is official, endorsed, sponsored, or maintained by
Alec unless you have separate written permission.

## What If I Need Permission Outside The Public License?

Ask first.

Use the contact details in `COMMERCIAL-LICENSE.md` and describe:

- what you are building
- whether the runtime will be modified
- how it will be distributed
- whether money is involved
- whether telemetry is only for your own Hytale project or for third parties
