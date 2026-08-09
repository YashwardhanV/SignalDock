# Reference Attribution

**SignalDock project owner:** Yashwardhan Verma  
**LinkedIn:** [linkedin.com/in/yashwardhanv](https://www.linkedin.com/in/yashwardhanv)  
**GitHub:** [github.com/YashwardhanV](https://github.com/YashwardhanV)  
**Public email:** [yashwardhanverma108@gmail.com](mailto:yashwardhanverma108@gmail.com)

## What was referenced

SignalDock was created after studying:

- **Repository:** [Webhook Delivery Platform](https://github.com/ErenKarakus1/Webhook-Delivery-Platform)
- **Author:** Eren Karakuş
- **License:** MIT License

The reference informed the domain study: endpoint registration, event routing, delivery attempts, timeouts, signing, retries, dead letters, and manual replay. It also provided an example of a Kafka/Redis/microservice architecture that SignalDock deliberately replaces with a PostgreSQL-backed modular monolith.

SignalDock is not presented as a renamed version of that repository. Its package structure, schema, REST contract, transaction boundaries, PostgreSQL queue/lease algorithm, security handling, tests, benchmark utility, documentation, and redesigned React interface were independently implemented for the SDE-1 scope owned and maintained by Yashwardhan Verma. No claim is made that the reference code was authored by the SignalDock owner.

## Reference license

```text
MIT License

Copyright (c) 2026 Eren Karakuş

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The copied license text above is preserved so the provenance remains clear even when this project is shared as a ZIP.
