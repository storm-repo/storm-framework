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
        'metamodel',
        'refs',
        'transactions',
        'spring-integration',
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
            'polymorphism',
            'entity-lifecycle',
            'validation',
            'json',
            'serialization',
          ],
        },
        {
          type: 'category',
          label: 'Operations',
          items: [
            'batch-streaming',
            'upserts',
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
          ],
        },
        {
          type: 'category',
          label: 'Operational',
          items: [
            'sql-logging',
            'configuration',
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
