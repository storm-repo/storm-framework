import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    'index',
    {
      type: 'category',
      label: 'Core Concepts',
      items: [
        'getting-started',
        'entities',
        'projections',
        'relationships',
        'repositories',
        'queries',
        'pagination-and-scrolling',
        'metamodel',
        'refs',
        'entity-design',
        'transactions',
        'spring-integration',
        'ktor-integration',
        'graalvm',
        'dialects',
        'testing',
      ],
    },
    {
      type: 'category',
      label: 'Advanced Topics',
      items: [
        {
          type: 'category',
          label: 'Entity Modeling',
          items: [
            'converters',
            'json',
            'polymorphism',
            'entity-lifecycle',
            'serialization',
            'validation',
          ],
        },
        {
          type: 'category',
          label: 'Operations',
          items: [
            'batch-streaming',
            'upserts',
            'write-sets',
          ],
        },
        {
          type: 'category',
          label: 'Internals',
          items: [
            'sql-templates',
            'hydration',
            'dirty-checking',
            'entity-cache',
            'cursors',
          ],
        },
        {
          type: 'category',
          label: 'Operational',
          items: [
            'configuration',
            'sql-logging',
            'metrics',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Resources',
      items: [
        'comparison',
        'faq',
        'migration-from-jpa',
        'jpa-cascades-vs-write-sets',
        'ai',
        'ai-reference',
        'database-and-mcp',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api-kotlin',
        'api-java',
      ],
    },
  ],
};

export default sidebars;
