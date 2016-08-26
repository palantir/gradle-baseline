# Web Application Checklist

- [ ] [Use a recommended **frontend technology stack**](#frontend-tech-stacks)
- [ ] [Use a standard **directory structure**](#directory-structure)
- [ ] [Have standard **build tasks**](#build-tasks)
- [ ] [Use a **style checker**](#style-checking)

## Frontend Technology Stack

Example of a recommended frontend tech stack:

- Languages
  - [TypeScript](http://www.typescriptlang.org/docs/tutorial.html):
    superset of JavaScript with types
  - [Less](http://lesscss.org/): CSS preprocessor
- Libraries
  - [React](https://facebook.github.io/react/docs/thinking-in-react.html):
        view library with virtual DOM and one-way data flow
  - [Redux](http://redux.js.org/): state library based on [Flux](http://facebook.github.io/flux/)
- Test tools
  - [Karma](http://karma-runner.github.io/0.13/intro/how-it-works.html): test runner
  - [Mocha](https://mochajs.org/): test framework
  - [Istanbul](https://gotwarlost.github.io/istanbul/): code coverage
- Build tools
  - [npm scripts](https://docs.npmjs.com/misc/scripts)
      (along with [npm-run-all](https://github.com/mysticatea/npm-run-all)): build scripts
  - [webpack](http://webpack.github.io/docs/): module bundler

## Directory Structure

- `build/`: compiled files and copied assets (ignored by git)
  - `build/min/`: output from production builds
  - `build/src/`: output from development builds
- `src/`: source files
- `test/`: test files
  - `test/tsconfig.json`: TypeScript compiler configuration for tests
  - `test/typings/`: typings for tests
- `package.json`: dependencies and build scripts
- `tsconfig.json`: TypeScript compiler configuration for source files
- `typings/`: typings for source files

## Build Tasks

The following tasks should be defined in the scripts` block in `package.json`:

- `npm run clean`: delete all files in `build/`

### Compilation

- `npm run buildDev`: build source files for development
- `npm run build`: build source files for production
- `npm run watch`: build source files for development and rebuild on file changes

### App Testing

- `npm test`: build and run tests once
- `npm run tdd`: build and run tests continuously

## Style Checking

- CSS/Less:   [stylelint](http://stylelint.io/)
- JavaScript: [eslint](http://eslint.org/)
- TypeScript: [tslint](http://palantir.github.io/tslint/)
