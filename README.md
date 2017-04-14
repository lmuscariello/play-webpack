play-webpack
------------

[![Codacy grade](https://img.shields.io/codacy/grade/4ca293d1006d4416a9aeb76bf323db6e.svg)](https://www.codacy.com/app/bowlingx/play-webpack?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=BowlingX/play-webpack&amp;utm_campaign=Badge_Grade)
[![CircleCI](https://img.shields.io/circleci/project/github/BowlingX/play-webpack.svg)](https://circleci.com/gh/BowlingX/play-webpack)
[![Maven Central](https://img.shields.io/maven-central/v/com.bowlingx/play-webpack_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22play-webpack_2.12%22)

This play module will add support for `webpack` and server-side rendering of javascript 
with the build-in `nashorn` script engine.

## Requirements

- JDK 8
- scala 2.11 or scala 2.12
- play 2.6
- webpack or anything the generates a JSON file like the one below

## Setup

Create a file in ~/project/play-webpack.sbt

    addSbtPlugin("com.bowlingx" %% "play-webpack-plugin" % "0.1.7")
    
Add the following dependencies:
    
    libraryDependencies += "com.bowlingx" %% "play-webpack" % "0.1.7"

The plugin will convert a webpack JSON manifest file (generated with https://github.com/kossnocorp/assets-webpack-plugin) to a scala object 
that can be used directly in play templates for example. The plugin is theoretically not limited to play. 
I will extend the project to support other frameworks in the future.

To make the assets available in your template file:

    TwirlKeys.templateImports += "com.bowlingx.webpack.WebpackManifest"

Sample JSON File (generated by `assets-webpack`):

    {
      "vendor": {
        "js": "/assets/compiled/vendor.js",
        "css": "/assets/compiled/vendor.css"
      },
      "server": {
        "js": "/assets/compiled/server.js"
      },
      "main": {
        "js": "/assets/compiled/main.js",
        "css": "/assets/compiled/main.css"
      },
      "manifest": {
        "js": "/assets/compiled/manifest.js"
      }
    }
    
Sample twirl Template:
    
    @(title: String)(content: Html)
    <!DOCTYPE html>
    <html lang="en">
        <head>
            <title>@title</title>
            @WebpackManifest.main.css.map { file =>
                <link rel="stylesheet" media="screen" href="@file">
            }
            <link rel="shortcut icon" type="image/png" href="@routes.Assets.versioned("images/favicon.png")">
        </head>
        <body>
            @content
            @Seq(WebpackManifest.manifest.js, WebpackManifest.vendor.js, WebpackManifest.main.js).flatten.map { file =>
                <script src="@file" type="text/javascript"></script>
            }
        </body>
    </html>

### Default Settings

The plugin defines the following configuration (your `application.conf`):

    webpack {
      # the default bundles to include
      prependBundles = ["manifest", "vendor"]
    
      # The object that is generated by the plugin (needs to implement `WebpackManifestType`)
      manifestClass = "com.bowlingx.webpack.WebpackManifest$"

      # The public path of the assets
      publicPath = "/assets/compiled"
      # The path where they are stored relative to project root
      serverPath = "/public/compiled"
      
      rendering {
          timeout = 1minute
          renderers {
            prod = 5
            dev = 1
            test = 1
          }
        }
    }
    
The default path of the manifest file (relative to project root) (in your `build.sbt`)
    
    webpackManifest := Option(file("conf/webpack-assets.json"))

# Server-Side Rendering with Nashorn

The main purpose of this plugin is to provide an easy way to render javascript inside a play action.
After enabling the module in you `application.conf`, you can hook up your controller like this:

    @Singleton
    class YourPlayController @Inject()(engine: Engine, components: ControllerComponents)(implicit context:ExecutionContext) extends AbstractController(components) {
    
      def index: Action[AnyContent] = Action.async {
        engine.render("yourGlobalMethod", "any", "list", "of", "arguments") map {
          case Success(Some(renderResult)) => Ok(renderResult.toString)
          case _ => NotFound
        }
      }
    }

See a full example in `src/play-module/src/test`.

**Important: Prevent modifying the global state of the JavaScript environment.**
Due to performance reasons, contexts are reused and any changes are persisted inside the engine if you do so.
To prevent memory leaks keep your methods pure.

You can configure the number of rendering actors in `webpack.rendering.renderers`:

```
rendering {
    timeout = 1minute
    renderers {
      prod = 5
      dev = 1
      test = 1
    }
  }
```

The more renderers you setup, the more requests you can handle. 
It defaults to 1 in tests and dev to speed up the init process.

## Promises

The library makes it possible to use an async result.
Support for `setTimeout` and `clearTimeout` exists since version `0.1.5`.

All this just requires to return a `Promise` in the render function. A simulated `Promise` looks like this:

    global.yourGlobalMethod = function () {
      return {
        then: function (resolve, reject) {
          setTimeout(function () {
            resolve("This is an async resolved String");
          }, 100);
        }
      };
    };

If you need `Promise` support in your library, use any polyfill available. This library does not ship with a polyfill.

Supported Libraries (tested by the Author)

- reactjs
- react-apollo (https://github.com/apollographql/react-apollo)

Sample integration Demo with apollo and sangria (http://sangria-graphql.org/) is on it's way.

## Workflow

Any file changes (including the manifest file) are picked up automatically and a recompilation is triggered, 
so the normal "change and reload" cycle that leads to a faster development experience is kept.

